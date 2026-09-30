// query, get och stats (skill db-access) – bara läsning. Endast emulatorn.
import { beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { getDocument, householdStats, parseWhere, queryCollection, resolveHousehold } from '../lib/query.mjs';
import { seed, useCleanEmulator } from './helpers/emulator.mjs';

const database = useCleanEmulator();
const toolsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const other = { path: 'households/annat', data: { name: 'Annat', memberUids: ['uid-x'], schemaVersion: 1 } };

beforeEach(() => seed(database()));

test('ett enda hushåll väljs automatiskt; relativa samlingar tolkas i det', async () => {
  const rows = await queryCollection(database(), 'persons/anna/regimens', { fields: ['name'] });
  assert.deepEqual(rows, [
    { id: 'd-vitamin', name: 'D-vitamin' },
    { id: 'levaxin', name: 'Levaxin' },
  ]);
});

test('--where filtrerar på likhet med tolkade värden, --limit begränsar', async () => {
  const db = database();
  assert.deepEqual((await queryCollection(db, 'persons/anna/regimens', { where: [parseWhere('kind=SUPPLEMENT')] })).map((r) => r.id), ['d-vitamin']);
  assert.deepEqual((await queryCollection(db, 'trips/gran-canaria/packingItems', { where: [parseWhere('checked=true')] })).map((r) => r.id), ['rad-1']);
  assert.deepEqual((await queryCollection(db, 'trips/gran-canaria/packingItems', { where: [parseWhere('quantity=9')] })).map((r) => r.id), ['rad-1']);
  assert.equal((await queryCollection(db, 'trips/gran-canaria/packingItems', { limit: 1 })).length, 1);
  assert.throws(() => parseWhere('utanlikhetstecken'), /fält=värde/);
});

test('--where tolkar bara strikta decimaltal som tal; citattecken tvingar text', () => {
  assert.equal(parseWhere('n=-12').value, -12);
  assert.equal(parseWhere('n=0.5').value, 0.5);
  assert.equal(parseWhere('n=007').value, 7);
  assert.equal(parseWhere('n="007"').value, '007');
  assert.equal(parseWhere('n= ').value, ' ');
  assert.equal(parseWhere('n=1e3').value, '1e3');
  assert.equal(parseWhere('n=0x10').value, '0x10');
  assert.equal(parseWhere('n="true"').value, 'true');
});

test('ett datafält som heter id skriver inte över dokument-ID:t', async () => {
  const db = database();
  await seed(db, [{ path: 'households/hushall-test/familyItems/riktigt-id', data: { id: 'falskt', name: 'X' } }]);
  const row = (await queryCollection(db, 'familyItems', { where: [parseWhere('name=X')] }))[0];
  assert.equal(row.id, 'riktigt-id');
  assert.equal(Object.keys(row)[0], 'id');
});

test('--household som inte finns, eller som inte matchar en full sökväg, ger fel', async () => {
  const db = database();
  await assert.rejects(queryCollection(db, 'persons', { household: 'finns-inte' }), /Hushållet finns-inte finns inte/);
  await assert.rejects(getDocument(db, 'households/hushall-test/persons/anna', { household: 'annat' }), /annat hushåll/);
});

test('flera hushåll kräver --household; households listar alla', async () => {
  const db = database();
  await seed(db, [other]);
  await assert.rejects(queryCollection(db, 'persons'), /Flera hushåll \(2\) – ange --household/);
  assert.equal((await queryCollection(db, 'persons', { household: 'hushall-test' })).length, 1);
  assert.deepEqual((await queryCollection(db, 'households', { fields: ['name'] })).map((r) => r.name).sort(), ['Annat', 'Familjen Test']);
});

test('okända samlingar, saknade dokument och saknade hushåll ger tydliga fel', async () => {
  const db = database();
  await assert.rejects(queryCollection(db, 'hemligt'), /Okänd samling/);
  await assert.rejects(getDocument(db, 'trips/finns-inte'), /Dokumentet finns inte: households\/hushall-test\/trips\/finns-inte/);
  await assert.rejects(householdStats(db, { household: 'finns-inte' }), /Hushållet finns-inte finns inte/);
  await db.recursiveDelete(db.collection('households'));
  await assert.rejects(resolveHousehold(db), /Inga hushåll/);
});

test('get visar ett dokument med tidsstämplar som ISO; relativ och full sökväg ger samma', async () => {
  const db = database();
  const relative = await getDocument(db, 'trips/gran-canaria');
  assert.deepEqual(relative, await getDocument(db, 'households/hushall-test/trips/gran-canaria'));
  assert.deepEqual(relative.createdAt, { __ts: '2026-09-02T10:00:00.000000000Z' });
});

test('stats visar schemaVersion och antal per samling, inget innehåll', async () => {
  const [stats] = await householdStats(database());
  assert.deepEqual(stats, {
    household: 'hushall-test',
    schemaVersion: 1,
    counts: { households: 1, persons: 1, regimens: 3, familyItems: 1, trips: 1, packingItems: 2 },
  });
});

test('skripten från kommandoraden: tabell, --json och --help', () => {
  const run = (script, args) => spawnSync(process.execPath, [path.join(toolsDir, script), ...args], { encoding: 'utf8' });
  const table = run('query.mjs', ['persons', '--fields', 'name']);
  assert.equal(table.status, 0, table.stderr);
  assert.match(table.stdout, /^id\s+name\n/);
  assert.match(table.stdout, /\(1 dokument\)/);
  assert.deepEqual(JSON.parse(run('query.mjs', ['persons', '--json', '--fields', 'name']).stdout), [{ id: 'anna', name: 'Anna' }]);
  assert.match(run('stats.mjs', []).stdout, /regimens: 3/);
  assert.match(run('get.mjs', ['persons/anna']).stdout, /"emoji"/);
  assert.match(run('get.mjs', ['--help']).stdout, /Användning: node tools\/db\/get.mjs/);
  assert.equal(run('query.mjs', []).status, 1);
});
