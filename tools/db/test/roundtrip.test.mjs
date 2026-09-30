// Regel 1: all data överlever export → radera → import → export (BCK-1, BCK-3). Endast emulatorn.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { exportData, importData, MAX_BATCH } from '../lib/backup.mjs';
import { COLLECTIONS } from '../lib/collections.mjs';
import { collectionOf } from '../lib/walk.mjs';
import { clearHouseholds, fixture, otherHousehold, seed, useCleanEmulator } from './helpers/emulator.mjs';

const database = useCleanEmulator();
const byPath = (docs) => [...docs].sort((a, b) => a.path.localeCompare(b.path));

test('fixturen täcker alla samlingar', () => {
  const covered = new Set(fixture.documents.map((d) => collectionOf(d.path)));
  assert.deepEqual([...covered].sort(), COLLECTIONS.map((c) => c.name).sort());
});

test('exporten innehåller exakt fixturens dokument, med tidsstämplar på mikrosekunden (Firestores precision)', async () => {
  const db = database();
  await seed(db);
  const exported = await exportData(db, { now: new Date('2026-10-01T00:00:00Z') });
  assert.equal(exported.exportedAt, '2026-10-01T00:00:00.000Z');
  assert.equal(exported.schemaVersion, 1);
  assert.deepEqual(byPath(exported.documents), byPath(fixture.documents));
});

test('rundtur: export → radera → import → export ger identiskt innehåll', async () => {
  const db = database();
  await seed(db);
  const first = await exportData(db);
  await clearHouseholds(db);
  assert.equal((await exportData(db)).documents.length, 0);

  const { written } = await importData(db, first);
  assert.equal(written.regimens, 3);

  const second = await exportData(db);
  assert.deepEqual(byPath(second.documents), byPath(first.documents));
});

test('--household exporterar och importerar bara det hushållet', async () => {
  const db = database();
  await seed(db);
  await seed(db, [otherHousehold]);
  const one = await exportData(db, { household: 'hushall-test' });
  assert.ok(one.documents.every((d) => d.path.startsWith('households/hushall-test')));

  const all = await exportData(db);
  await clearHouseholds(db);
  await importData(db, all, { household: 'annat' });
  assert.deepEqual((await exportData(db)).documents.map((d) => d.path), ['households/annat']);
});

test('torrkörning skriver ingenting', async () => {
  const db = database();
  const { written } = await importData(db, { schemaVersion: 1, documents: fixture.documents }, { dryRun: true });
  assert.equal(written.households, 1);
  assert.equal((await exportData(db)).documents.length, 0);
});

test(`import delar upp i batchar om ${MAX_BATCH}`, async () => {
  const db = database();
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
  const db = database();
  await seed(db);
  await seed(db, [{ path: 'households/trasigt/persons/p1', data: { name: 'Kvar' } }]);
  const paths = (await exportData(db)).documents.map((d) => d.path);
  assert.ok(paths.includes('households/trasigt/persons/p1'));
  assert.ok(paths.includes('households/hushall-test'));
  await assert.rejects(exportData(db, { household: 'finns-inte' }), /finns inte/);
});

test('--replace gör hushållet exakt som i filen: dokument som tillkommit efter backupen tas bort', async () => {
  const db = database();
  await seed(db);
  await seed(db, [otherHousehold]);
  const backup = await exportData(db, { household: 'hushall-test' });
  await seed(db, [
    { path: 'households/hushall-test/trips/gran-canaria/packingItems/ny', data: { name: 'Tillkommen' } },
    { path: 'households/hushall-test/persons/ny', data: { name: 'Ny' } },
  ]);

  const dry = await importData(db, backup, { replace: true, dryRun: true });
  assert.deepEqual(dry.removed, { packingItems: 1, persons: 1 });
  assert.equal((await exportData(db, { household: 'hushall-test' })).documents.length, fixture.documents.length + 2);

  await importData(db, backup, { replace: true });
  assert.deepEqual(byPath((await exportData(db, { household: 'hushall-test' })).documents), byPath(backup.documents));
  assert.ok((await exportData(db)).documents.some((d) => d.path === 'households/annat'), 'andra hushåll rörs inte');
});

test('import skriver ingenting om ett dokument längre fram i filen är trasigt', async () => {
  const db = database();
  const documents = [
    { path: 'households/h', data: { memberUids: ['uid-x'], schemaVersion: 1 } },
    ...Array.from({ length: MAX_BATCH + 5 }, (_, i) => ({ path: `households/h/persons/p${i}`, data: { name: `P${i}` } })),
    { path: 'households/h/persons/trasig', data: { at: { __ts: 'inte ett datum' } } },
  ];
  await assert.rejects(importData(db, { schemaVersion: 1, documents }), /households\/h\/persons\/trasig: ogiltig tidsstämpel/);
  assert.equal((await exportData(db)).documents.length, 0);
});

test('en rotsamling utanför listan stoppar exporten', async () => {
  const db = database();
  await seed(db);
  await db.doc('okand/x').set({ a: 1 });
  try {
    await assert.rejects(exportData(db), /Okänd samling okand/);
  } finally {
    await db.recursiveDelete(db.collection('okand'));
  }
});

test('en samling som saknas i collections.mjs stoppar exporten i stället för att tappas', async () => {
  const db = database();
  await seed(db, [
    { path: 'households/h', data: { memberUids: ['uid-x'], schemaVersion: 1 } },
    { path: 'households/h/hemlig/x', data: { a: 1 } },
  ]);
  await assert.rejects(exportData(db), /Okänd samling households\/h\/hemlig/);
});
