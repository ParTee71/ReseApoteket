// Skripten som backup.yml och sessionen kör: export.mjs och import.mjs från kommandoraden.
// Endast emulatorn (barnprocessen ärver NODE_TEST_CONTEXT och är därmed i testläge).
import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { fixture, seed, useCleanEmulator } from './helpers/emulator.mjs';

const toolsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const node = (script, args) => spawnSync(process.execPath, [path.join(toolsDir, script), ...args], { encoding: 'utf8' });

const database = useCleanEmulator();
let dir;
before(() => {
  dir = mkdtempSync(path.join(tmpdir(), 'tools-db-'));
});
beforeEach(() => seed(database()));
after(() => rmSync(dir, { recursive: true, force: true }));

test('export.mjs skriver filen och visar bara antal per samling', () => {
  const out = path.join(dir, 'backup.json');
  const result = node('export.mjs', ['--out', out]);
  assert.equal(result.status, 0, result.stderr);
  assert.equal(JSON.parse(readFileSync(out, 'utf8')).documents.length, fixture.documents.length);
  assert.match(result.stdout, /regimens: 3/);
  assert.doesNotMatch(result.stdout, /Levaxin/, 'utskriften får inte innehålla dokumentinnehåll');
});

test('import.mjs --dry-run visar vad som skulle skrivas', () => {
  const out = path.join(dir, 'backup.json');
  execFileSync(process.execPath, [path.join(toolsDir, 'export.mjs'), '--out', out]);
  const result = node('import.mjs', ['--in', out, '--dry-run']);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Torrkörning/);
  assert.match(result.stdout, /households: 1/);
});

test('fel ger en rad på svenska och exitkod 1; --help fungerar', () => {
  const missing = node('export.mjs', []);
  assert.equal(missing.status, 1);
  assert.match(missing.stderr, /^Fel: --out saknas/);
  assert.match(node('import.mjs', ['--help']).stdout, /Användning: node tools\/db\/import.mjs/);
  assert.equal(node('migrate.mjs', ['--okänd']).status, 1);
});

test('printTable: kolumner från alla rader, avkortade celler markeras och radbrytningar syns', async () => {
  const { printTable } = await import('../lib/cli.mjs');
  const lines = [];
  const log = console.log;
  console.log = (line) => lines.push(line);
  try {
    printTable([{ id: 'a', name: 'x'.repeat(60) }, { id: 'b', notes: 'rad ett\nrad två' }]);
    printTable([]);
  } finally {
    console.log = log;
  }
  assert.match(lines[0], /^id\s+name\s+notes$/);
  assert.ok(lines[2].includes(`${'x'.repeat(39)}…`));
  assert.ok(lines[3].includes('rad ett ⏎ rad två'));
  assert.equal(lines[4], '(2 dokument)');
  assert.equal(lines[5], '(inga dokument)');
});
