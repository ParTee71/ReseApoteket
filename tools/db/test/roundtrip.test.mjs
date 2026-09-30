// Regel 1: all data överlever export → radera → import → export (BCK-1, BCK-3). Endast emulatorn.
import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { exportData, importData, MAX_BATCH } from '../lib/backup.mjs';
import { COLLECTIONS } from '../lib/collections.mjs';
import { collectionOf } from '../lib/walk.mjs';
import { adminFirestore, clearHouseholds, fixture, seed } from './helpers/emulator.mjs';

let db;
const byPath = (docs) => [...docs].sort((a, b) => a.path.localeCompare(b.path));

before(() => {
  db = adminFirestore();
});

beforeEach(async () => {
  await clearHouseholds(db);
});

after(async () => {
  await clearHouseholds(db);
});

test('fixturen täcker alla samlingar', () => {
  const covered = new Set(fixture.documents.map((d) => collectionOf(d.path)));
  assert.deepEqual([...covered].sort(), COLLECTIONS.map((c) => c.name).sort());
});

test('exporten innehåller exakt fixturens dokument, med tidsstämplar på mikrosekunden (Firestores precision)', async () => {
  await seed(db);
  const exported = await exportData(db, { now: new Date('2026-10-01T00:00:00Z') });
  assert.equal(exported.exportedAt, '2026-10-01T00:00:00.000Z');
  assert.equal(exported.schemaVersion, 1);
  assert.deepEqual(byPath(exported.documents), byPath(fixture.documents));
});

test('rundtur: export → radera → import → export ger identiskt innehåll', async () => {
  await seed(db);
  const first = await exportData(db);
  await clearHouseholds(db);
  assert.equal((await exportData(db)).documents.length, 0);

  const counts = await importData(db, first);
  assert.equal(counts.regimens, 3);

  const second = await exportData(db);
  assert.deepEqual(byPath(second.documents), byPath(first.documents));
});

test('--household exporterar och importerar bara det hushållet', async () => {
  await seed(db);
  await seed(db, [{ path: 'households/annat', data: { name: 'Annat', memberUids: ['uid-x'], schemaVersion: 1 } }]);
  const one = await exportData(db, { household: 'hushall-test' });
  assert.ok(one.documents.every((d) => d.path.startsWith('households/hushall-test')));

  const all = await exportData(db);
  await clearHouseholds(db);
  await importData(db, all, { household: 'annat' });
  assert.deepEqual((await exportData(db)).documents.map((d) => d.path), ['households/annat']);
});

test('torrkörning skriver ingenting', async () => {
  const counts = await importData(db, { schemaVersion: 1, documents: fixture.documents }, { dryRun: true });
  assert.equal(counts.households, 1);
  assert.equal((await exportData(db)).documents.length, 0);
});

test(`import delar upp i batchar om ${MAX_BATCH}`, async () => {
  const documents = [
    { path: 'households/stor', data: { name: 'Stor', memberUids: ['uid-x'], schemaVersion: 1 } },
    ...Array.from({ length: MAX_BATCH + 20 }, (_, i) => ({
      path: `households/stor/familyItems/item-${String(i).padStart(4, '0')}`,
      data: { name: `Sak ${i}`, sortOrder: i },
    })),
  ];
  await importData(db, { schemaVersion: 1, documents });
  assert.equal((await exportData(db)).documents.length, MAX_BATCH + 21);
});

test('ett hushåll utan eget dokument exporteras ändå och fäller inte backupen', async () => {
  await seed(db);
  await seed(db, [{ path: 'households/trasigt/persons/p1', data: { name: 'Kvar' } }]);
  const paths = (await exportData(db)).documents.map((d) => d.path);
  assert.ok(paths.includes('households/trasigt/persons/p1'));
  assert.ok(paths.includes('households/hushall-test'));
  await assert.rejects(exportData(db, { household: 'finns-inte' }), /finns inte/);
});

test('en samling som saknas i collections.mjs stoppar exporten i stället för att tappas', async () => {
  await seed(db, [
    { path: 'households/h', data: { memberUids: ['uid-x'], schemaVersion: 1 } },
    { path: 'households/h/hemlig/x', data: { a: 1 } },
  ]);
  await assert.rejects(exportData(db), /Okänd samling households\/h\/hemlig/);
});
