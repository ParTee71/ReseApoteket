// migrate.mjs: stämplar hushållet sist och bara när alla steg finns. Endast emulatorn.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { exportData } from '../lib/backup.mjs';
import { migrateHouseholds } from '../lib/migrate.mjs';
import { fixture, seed, useCleanEmulator } from './helpers/emulator.mjs';

const database = useCleanEmulator();

test('hushåll som redan har versionen lämnas orörda', async () => {
  const db = database();
  await seed(db);
  assert.deepEqual(await migrateHouseholds(db, 1), {});
  assert.deepEqual((await exportData(db)).documents.length, fixture.documents.length);
});

test('en version nyare än verktyget vägras', async () => {
  const db = database();
  await assert.rejects(migrateHouseholds(db, 2), /nyare än verktygets/);
});

test('ett steg lyfter alla dokument och hushållet stämplas med den nya versionen', async () => {
  const db = database();
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

test('ett hushåll utan hushållsdokument hoppas över utan att stoppa de andra', async () => {
  const db = database();
  await seed(db, [
    { path: 'households/a-trasigt/persons/p1', data: { namn: 'Kvar' } },
    { path: 'households/b-gammalt', data: { memberUids: ['uid-x'], schemaVersion: 0 } },
  ]);
  const steps = { 0: (_, doc) => doc };
  assert.deepEqual(await migrateHouseholds(db, 1, { steps }), {
    'hoppade över (saknar hushållsdokument)': 1,
    'hushåll från v0': 1,
  });
});

test('saknat steg stoppar innan något skrivs', async () => {
  const db = database();
  await seed(db, [{ path: 'households/gammalt', data: { memberUids: ['uid-x'], schemaVersion: 0 } }]);
  await assert.rejects(migrateHouseholds(db, 1), /Migreringssteg saknas för version 0/);
});
