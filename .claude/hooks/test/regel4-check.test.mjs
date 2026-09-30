// Test för regel4-check-hooken. Körs med: node --test '.claude/hooks/test/*.test.mjs'
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, copyFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { scopesFor, parsePatterns, findViolations, findRepoRoot } from '../regel4-check.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '../../..');
const hook = path.join(repoRoot, '.claude/hooks/regel4-check.mjs');
const patternsRel = 'app/src/test/resources/ui-forbidden.txt';
const allowRel = 'app/src/test/resources/ui-allowlist.txt';
const pkg = 'app/src/main/kotlin/se/partee71/reseapoteket';

const readPatterns = () => readFileSync(path.join(repoRoot, patternsRel), 'utf8');

/** Tillfälligt repo (i en egen föräldrakatalog) med de riktiga mönster- och undantagsfilerna. */
function makeProject(allowlist) {
  const parent = mkdtempSync(path.join(tmpdir(), 'regel4-'));
  const dir = path.join(parent, 'ReseApoteket');
  mkdirSync(path.join(dir, 'app/src/test/resources'), { recursive: true });
  copyFileSync(path.join(repoRoot, patternsRel), path.join(dir, patternsRel));
  if (allowlist !== undefined) writeFileSync(path.join(dir, allowRel), allowlist);
  else copyFileSync(path.join(repoRoot, allowRel), path.join(dir, allowRel));
  return { dir, parent };
}

function writeKt(dir, rel, content) {
  const abs = path.join(dir, rel);
  mkdirSync(path.dirname(abs), { recursive: true });
  writeFileSync(abs, content);
  return abs;
}

function runHook(projectDir, filePath, tool = 'Write') {
  return spawnSync('node', [hook], {
    input: JSON.stringify({ tool_name: tool, tool_input: { file_path: filePath }, cwd: projectDir }),
    env: { ...process.env, CLAUDE_PROJECT_DIR: projectDir },
    encoding: 'utf8',
  });
}

const featureCard = `package x\n\n@Composable\nfun PersonRow() {\n    Card(modifier = Modifier) { Text("Anna") }\n}\n`;

test('stoppar Card( i feature-kod med exit 2 och tipsar om ApotekCard', () => {
  const { dir } = makeProject();
  const r = runHook(dir, writeKt(dir, `${pkg}/ui/persons/PersonRow.kt`, featureCard));
  assert.equal(r.status, 2);
  assert.match(r.stderr, /PersonRow\.kt:5/);
  assert.match(r.stderr, /ApotekCard/);
});

test('fungerar när sessionens projektrot är en föräldrakatalog med flera repon', () => {
  const { dir, parent } = makeProject();
  const r = runHook(parent, writeKt(dir, `${pkg}/ui/persons/PersonRow.kt`, featureCard));
  assert.equal(r.status, 2, 'hooken måste hitta repot från filen, inte från projektroten');
  assert.match(r.stderr, new RegExp(`${pkg}/ui/persons/PersonRow\\.kt:5`));
});

test('släpper igenom samma rad i ui/components', () => {
  const { dir } = makeProject();
  const r = runHook(dir, writeKt(dir, `${pkg}/ui/components/ApotekCard.kt`, featureCard));
  assert.equal(r.status, 0, r.stderr);
  assert.equal(r.stderr, '');
});

test('delade komponenter och tillåtna primitiver släpps i feature-kod', () => {
  const scopes = parsePatterns(readPatterns());
  const ok = [
    'ApotekCard { }', 'AppButton(onClick = {})', 'AppIconButton(onClick = {})', 'AddSplitButton()',
    'AppTextField(value = "")', 'AppTopBar(title = "")', 'AppFilterChip(selected = true)',
    'PersonChip()', 'AppSnackbarHost(state)', 'UndoSnackbar()', 'CheckRow(checked = true)',
    'SwitchRow(checked = true)', 'AppSegmentedChoice()', 'AppMenu()', 'DateField(value)',
    'AppDivider()', 'ChipRow { }', 'InfoPill()', 'AppFloatingToolbar()', 'QuantityStepper()',
    'Text("Hej", style = AppTypography.itemTitle)', 'Icon(Icons.Rounded.Luggage, null)',
    'Spacer(Modifier.height(Spacing.l))', 'Modifier.padding(Spacing.m)',
    'import androidx.compose.material3.Card', '// Card(modifier) i en kommentar',
  ];
  for (const line of ok) {
    assert.deepEqual(findViolations(`${pkg}/ui/trips/TripRow.kt`, line, scopes, []), [], line);
  }
});

test('hittar hårdkodat utseende, råa M3-komponenter och egna ramelement', () => {
  const scopes = parsePatterns(readPatterns());
  const bad = [
    'val c = Color(0xFFC8502F)', 'tint = Color.Red', 'shape = RoundedCornerShape(12.dp)',
    'Text("x", fontSize = 14.sp)', 'lineHeight = 20.sp', 'Modifier.height(48.dp)',
    'Modifier.width(120.dp)', 'Modifier.padding(16.dp)', 'Modifier.padding(horizontal = 16.dp)',
    'LazyColumn { }', 'LazyRow { }', 'Scaffold(topBar = {})', 'AlertDialog(onDismissRequest = {})',
    'SwipeToDismissBox(state) { }', 'FloatingActionButton(onClick = {})', 'Switch(checked = true)',
    'SingleChoiceSegmentedButtonRow { }', 'DropdownMenu(expanded = true)', 'DatePicker(state)',
    'HorizontalDivider()', 'Surface(color = x)', 'Badge { }', 'NavigationBar { }',
  ];
  for (const line of bad) {
    assert.ok(findViolations(`${pkg}/ui/packing/PackingScreen.kt`, line, scopes, []).length > 0, line);
  }
});

test('Firestore-typer stoppas överallt utom i data/firestore – även i di/ och ui/', () => {
  const { dir } = makeProject();
  const src = 'class X(val db: FirebaseFirestore)\n';
  assert.equal(runHook(dir, writeKt(dir, `${pkg}/data/repository/PersonRepository.kt`, src), 'Edit').status, 2);
  assert.equal(runHook(dir, writeKt(dir, `${pkg}/di/FirestoreModule.kt`, src), 'Edit').status, 2);
  assert.equal(runHook(dir, writeKt(dir, `${pkg}/ui/components/Bad.kt`, src), 'Edit').status, 2);
  assert.equal(runHook(dir, writeKt(dir, `${pkg}/data/firestore/FirestoreModule.kt`, src), 'Edit').status, 0);
});

test('allowlist-rad släpper igenom just den filen och symbolen', () => {
  const { dir } = makeProject(`# test\n${pkg}/ui/persons/PersonRow.kt:Card( – test\n`);
  assert.equal(runHook(dir, writeKt(dir, `${pkg}/ui/persons/PersonRow.kt`, featureCard)).status, 0);
});

test('scope per sökväg', () => {
  assert.deepEqual(scopesFor('README.md'), []);
  assert.deepEqual(scopesFor(`${pkg}/ui/persons/notes.md`), []);
  assert.deepEqual(scopesFor('app/src/test/kotlin/X.kt'), []);
  assert.deepEqual(scopesFor(`${pkg}/data/firestore/FirestoreCollection.kt`), []);
  assert.deepEqual(scopesFor(`${pkg}/ui/theme/Theme.kt`), ['firestore']);
  assert.deepEqual(scopesFor(`${pkg}/ui/common/EditorState.kt`), ['firestore']);
  assert.deepEqual(scopesFor(`${pkg}/ui/components/screens/EntityListScreen.kt`), ['firestore']);
  assert.deepEqual(scopesFor(`${pkg}/ui/persons/PersonsScreen.kt`), ['feature', 'firestore']);
  assert.deepEqual(scopesFor(`${pkg}/ui/AppRoot.kt`), ['feature', 'firestore']);
  assert.deepEqual(scopesFor(`${pkg}/di/AppModule.kt`), ['firestore']);
});

test('filer utanför repo, utan mönsterfil eller av annan typ ignoreras', () => {
  const { dir, parent } = makeProject();
  assert.equal(runHook(dir, writeKt(dir, 'docs/Card.kt', featureCard)).status, 0);
  const outside = writeKt(parent, `annat-repo/${pkg}/ui/persons/X.kt`, featureCard);
  assert.equal(findRepoRoot(outside), null);
  assert.equal(runHook(parent, outside).status, 0);
});

test('tom eller trasig indata ger exit 0', () => {
  assert.equal(spawnSync('node', [hook], { input: 'inte json', encoding: 'utf8' }).status, 0);
});

test('mönsterfilen har båda scopen, giltiga regexar och en ersättning per rad', () => {
  const scopes = parsePatterns(readPatterns());
  assert.deepEqual(Object.keys(scopes).sort(), ['feature', 'firestore']);
  assert.ok(scopes.feature.length >= 30);
  assert.ok(scopes.firestore.length >= 3);
  for (const rule of [...scopes.feature, ...scopes.firestore]) assert.ok(rule.replacement, rule.source);
});

test('hook-kommandona i settings.json hittar skripten både i repot och från en föräldrakatalog', () => {
  const settings = JSON.parse(readFileSync(path.join(repoRoot, '.claude/settings.json'), 'utf8'));
  const postCmd = settings.hooks.PostToolUse[0].hooks[0].command;
  const startCmd = settings.hooks.SessionStart[0].hooks[0].command;

  const { dir, parent } = makeProject();
  mkdirSync(path.join(dir, '.claude/hooks'), { recursive: true });
  for (const f of ['regel4-check.mjs', 'session-start.sh']) {
    copyFileSync(path.join(repoRoot, '.claude/hooks', f), path.join(dir, '.claude/hooks', f));
  }
  const file = writeKt(dir, `${pkg}/ui/persons/PersonRow.kt`, featureCard);
  const input = JSON.stringify({ tool_name: 'Write', tool_input: { file_path: file } });

  for (const projectDir of [dir, parent]) {
    const post = spawnSync('bash', ['-c', postCmd], {
      input, env: { ...process.env, CLAUDE_PROJECT_DIR: projectDir }, encoding: 'utf8',
    });
    assert.equal(post.status, 2, `PostToolUse med projektrot ${projectDir}: ${post.stderr}`);

    const start = spawnSync('bash', ['-c', startCmd], {
      env: { ...process.env, CLAUDE_PROJECT_DIR: projectDir, CLAUDE_CODE_REMOTE: 'true', FIREBASE_SERVICE_ACCOUNT: '' },
      encoding: 'utf8',
    });
    assert.equal(start.status, 0, start.stderr);
    assert.match(start.stdout, /FIREBASE_SERVICE_ACCOUNT: saknas/);
    assert.doesNotMatch(start.stdout, /eyJ|BEGIN PRIVATE KEY/, 'nyckelinnehåll får aldrig skrivas ut');
  }

  const none = spawnSync('bash', ['-c', postCmd], {
    input, env: { ...process.env, CLAUDE_PROJECT_DIR: mkdtempSync(path.join(tmpdir(), 'tom-')) }, encoding: 'utf8',
  });
  assert.equal(none.status, 0, 'utan hook i projektet ska kommandot vara tyst');
});
