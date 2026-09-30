// migrate.mjs: stämplar hushållet sist och bara när alla steg finns. Endast emulatorn.
import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { exportData } from '../lib/backup.mjs';
import { migrateHouseholds } from '../lib/migrate.mjs';
import { adminFirestore, clearHouseholds, fixture, seed } from './helpers/emulator.mjs';

let db;
before(() => {
  db = adminFirestore();
});
beforeEach(async () => {
  await clearHouseholds(db);
});
after(async () => {
  await clearHouseholds(db);
});

test('hushåll som redan har versionen lämnas orörda', async () => {
  await seed(db);
  assert.deepEqual(await migrateHouseholds(db, 1), {});
  assert.deepEqual((await exportData(db)).documents.length, fixture.documents.length);
});

test('en version nyare än verktyget vägras', async () => {
  await assert.rejects(migrateHouseholds(db, 2), /nyare än verktygets/);
});

test('ett steg lyfter alla dokument och hushållet stämplas med den nya versionen', async () => {
  await seed(db, [
    { path: 'households/gammalt', data: { name: 'Gammalt', memberUids: ['uid-x'], schemaVersion: 0 } },
    { path: 'households/gammalt/persons/p1', data: { namn: 'Anna' } },
  ]);
  // Ett påhittat steg 0 → 1 visar mekaniken utan att ändra verktygets riktiga version.
  const steps = { 0: (collection, doc) => (collection === 'persons' && 'namn' in doc ? { name: doc.namn } : doc) };

  assert.deepEqual(await migrateHouseholds(db, 1, { steps }), { 'hushåll från v0': 1 });

  const docs = Object.fromEntries((await exportData(db)).documents.map((d) => [d.path, d.data]));
  assert.deepEqual(docs['households/gammalt/persons/p1'], { name: 'Anna' });
  assert.equal(docs['households/gammalt'].schemaVersion, 1);
});

test('saknat steg stoppar innan något skrivs', async () => {
  await seed(db, [{ path: 'households/gammalt', data: { memberUids: ['uid-x'], schemaVersion: 0 } }]);
  await assert.rejects(migrateHouseholds(db, 1), /Migreringssteg saknas för version 0/);
});
