// Skripten som backup.yml och sessionen kör: export.mjs och import.mjs från kommandoraden.
// Endast emulatorn (barnprocessen ärver NODE_TEST_CONTEXT och är därmed i testläge).
import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { adminFirestore, clearHouseholds, fixture, seed } from './helpers/emulator.mjs';

const toolsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const node = (script, args) => spawnSync(process.execPath, [path.join(toolsDir, script), ...args], { encoding: 'utf8' });

let db;
let dir;
before(async () => {
  db = adminFirestore();
  dir = mkdtempSync(path.join(tmpdir(), 'tools-db-'));
  await clearHouseholds(db);
  await seed(db);
});
after(async () => {
  await clearHouseholds(db);
  rmSync(dir, { recursive: true, force: true });
});

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
