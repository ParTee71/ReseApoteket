// Test för regel4-check-hooken. Körs med: node --test .claude/hooks/test/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, copyFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { scopeOf, parsePatterns, findViolations } from '../regel4-check.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '../../..');
const hook = path.join(repoRoot, '.claude/hooks/regel4-check.mjs');
const patternsRel = 'app/src/test/resources/ui-forbidden.txt';
const allowRel = 'app/src/test/resources/ui-allowlist.txt';
const pkg = 'app/src/main/kotlin/se/partee71/reseapoteket';

/** Tillfälligt projekt med de riktiga mönster- och undantagsfilerna. */
function makeProject(allowlist) {
  const dir = mkdtempSync(path.join(tmpdir(), 'regel4-'));
  mkdirSync(path.join(dir, 'app/src/test/resources'), { recursive: true });
  copyFileSync(path.join(repoRoot, patternsRel), path.join(dir, patternsRel));
  if (allowlist !== undefined) writeFileSync(path.join(dir, allowRel), allowlist);
  else copyFileSync(path.join(repoRoot, allowRel), path.join(dir, allowRel));
  return dir;
}

function writeKt(dir, rel, content) {
  const abs = path.join(dir, rel);
  mkdirSync(path.dirname(abs), { recursive: true });
  writeFileSync(abs, content);
  return abs;
}

function runHook(dir, filePath, tool = 'Write') {
  return spawnSync('node', [hook], {
    input: JSON.stringify({ tool_name: tool, tool_input: { file_path: filePath }, cwd: dir }),
    env: { ...process.env, CLAUDE_PROJECT_DIR: dir },
    encoding: 'utf8',
  });
}

const featureCard = `package x\n\n@Composable\nfun PersonRow() {\n    Card(modifier = Modifier) { Text("Anna") }\n}\n`;

test('stoppar Card( i feature-kod med exit 2 och tipsar om ApotekCard', () => {
  const dir = makeProject();
  const file = writeKt(dir, `${pkg}/ui/persons/PersonRow.kt`, featureCard);
  const r = runHook(dir, file);
  assert.equal(r.status, 2);
  assert.match(r.stderr, /PersonRow\.kt:5/);
  assert.match(r.stderr, /ApotekCard/);
});

test('släpper igenom samma rad i ui/components', () => {
  const dir = makeProject();
  const file = writeKt(dir, `${pkg}/ui/components/ApotekCard.kt`, featureCard);
  const r = runHook(dir, file);
  assert.equal(r.status, 0, r.stderr);
  assert.equal(r.stderr, '');
});

test('delade komponenter med liknande namn är tillåtna i feature-kod', () => {
  const dir = makeProject();
  const ok = [
    'ApotekCard { }', 'AppButton(onClick = {})', 'AppIconButton(onClick = {})',
    'AddSplitButton()', 'AppTextField(value = "")', 'AppTopBar(title = "")',
    'AppFilterChip(selected = true)', 'AppSnackbarHost(state)', 'UndoSnackbar()',
    'CheckRow(checked = true)', 'Modifier.padding(Spacing.l)', 'import androidx.compose.material3.Card',
    '// Card(modifier) i en kommentar',
  ].join('\n');
  const file = writeKt(dir, `${pkg}/ui/trips/TripRow.kt`, ok);
  const r = runHook(dir, file);
  assert.equal(r.status, 0, r.stderr);
});

test('hittar hårdkodat utseende och råa ramelement', () => {
  const scopes = parsePatterns(readPatterns());
  const content = [
    'val c = Color(0xFFC8502F)',
    'shape = RoundedCornerShape(12.dp)',
    'Text("x", fontSize = 14.sp)',
    'LazyColumn { }',
    'Scaffold(topBar = {})',
    'Modifier.height(48.dp)',
    'Modifier.padding(16.dp)',
    'AlertDialog(onDismissRequest = {})',
    'SwipeToDismissBox(state) { }',
  ].join('\n');
  const hits = findViolations(`${pkg}/ui/packing/PackingScreen.kt`, content, scopes, []);
  assert.deepEqual(hits.map((h) => h.line), [1, 2, 3, 4, 5, 6, 7, 8, 9]);
});

test('Firestore utanför data/firestore stoppas, inuti tillåts', () => {
  const dir = makeProject();
  const repo = writeKt(dir, `${pkg}/data/repository/PersonRepository.kt`, 'class X(val db: FirebaseFirestore)\n');
  assert.equal(runHook(dir, repo, 'Edit').status, 2);
  const coll = writeKt(dir, `${pkg}/data/firestore/FirestoreCollection.kt`, 'class X(val db: FirebaseFirestore)\n');
  assert.equal(runHook(dir, coll, 'Edit').status, 0);
});

test('allowlist-rad släpper igenom just den filen och symbolen', () => {
  const dir = makeProject(`# test\n${pkg}/ui/persons/PersonRow.kt:Card( – test\n`);
  const file = writeKt(dir, `${pkg}/ui/persons/PersonRow.kt`, featureCard);
  assert.equal(runHook(dir, file).status, 0);
});

test('filer utanför app/src/main och andra filtyper ignoreras', () => {
  const dir = makeProject();
  assert.equal(scopeOf('README.md'), null);
  assert.equal(scopeOf(`${pkg}/ui/persons/notes.md`), null);
  assert.equal(scopeOf('app/src/test/kotlin/X.kt'), null);
  assert.equal(scopeOf(`${pkg}/ui/theme/Theme.kt`), null);
  assert.equal(scopeOf(`${pkg}/ui/common/EditorState.kt`), null);
  assert.equal(scopeOf(`${pkg}/ui/persons/PersonsScreen.kt`), 'feature');
  const file = writeKt(dir, 'docs/Card.kt', featureCard);
  assert.equal(runHook(dir, file).status, 0);
});

test('tom eller trasig indata ger exit 0', () => {
  const r = spawnSync('node', [hook], { input: 'inte json', encoding: 'utf8' });
  assert.equal(r.status, 0);
});

test('mönsterfilen har båda scopen och giltiga regexar', () => {
  const scopes = parsePatterns(readPatterns());
  assert.ok(scopes.feature.length >= 20);
  assert.ok(scopes.data.length >= 3);
  for (const rule of [...scopes.feature, ...scopes.data]) assert.ok(rule.replacement, rule.source);
});

function readPatterns() {
  return readFileSync(path.join(repoRoot, patternsRel), 'utf8');
}
