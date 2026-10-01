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

// Verktygets riktiga version är den första, så testerna låtsas att version 2 finns (current)
// med ett påhittat steg 1 → 2 – mekaniken utan att ändra formatet.
const v2 = { current: 2 };

test('ett steg lyfter alla dokument och hushållet stämplas med den nya versionen', async () => {
  const db = database();
  await seed(db, [
    { path: 'households/gammalt', data: { name: 'Gammalt', memberUids: ['uid-x'], schemaVersion: 1 } },
    { path: 'households/gammalt/persons/p1', data: { namn: 'Anna' } },
  ]);
  const steps = { 1: (collection, doc) => (collection === 'persons' && 'namn' in doc ? { name: doc.namn } : doc) };

  assert.deepEqual(await migrateHouseholds(db, 2, { ...v2, steps }), { 'hushåll från v1': 1 });

  const docs = Object.fromEntries((await exportData(db)).documents.map((d) => [d.path, d.data]));
  assert.deepEqual(docs['households/gammalt/persons/p1'], { name: 'Anna' });
  assert.equal(docs['households/gammalt'].schemaVersion, 2);
});

test('en trasig version under den första migreras som den första', async () => {
  const db = database();
  await seed(db, [{ path: 'households/noll', data: { memberUids: ['uid-x'], schemaVersion: 0 } }]);
  assert.deepEqual(await migrateHouseholds(db, 1), {}, 'version 0 räknas som 1 och är redan klar');
  assert.deepEqual(await migrateHouseholds(db, 2, { ...v2, steps: { 1: (_, doc) => doc } }), { 'hushåll från v1': 1 });
});

test('ett hushåll utan hushållsdokument hoppas över utan att stoppa de andra', async () => {
  const db = database();
  await seed(db, [
    { path: 'households/a-trasigt/persons/p1', data: { namn: 'Kvar' } },
    { path: 'households/b-gammalt', data: { memberUids: ['uid-x'], schemaVersion: 1 } },
  ]);
  const steps = { 1: (_, doc) => doc };
  assert.deepEqual(await migrateHouseholds(db, 2, { ...v2, steps }), {
    'hoppade över (saknar hushållsdokument)': 1,
    'hushåll från v1': 1,
  });
});

test('ett hushåll som inte går att migrera rapporteras och stoppar inte de andra', async () => {
  const db = database();
  await seed(db, [
    { path: 'households/a-trasigt', data: { memberUids: ['uid-x'], schemaVersion: 1 } },
    { path: 'households/a-trasigt/persons/p1', data: { name: 'Går inte' } },
    { path: 'households/b-friskt', data: { memberUids: ['uid-x'], schemaVersion: 1 } },
  ]);
  const steps = {
    1: (collection, doc) => {
      if (collection === 'persons') throw new Error('steget klarar inte dokumentet');
      return doc;
    },
  };

  const result = await migrateHouseholds(db, 2, { ...v2, steps });

  assert.deepEqual(result.fel, ['a-trasigt: steget klarar inte dokumentet']);
  assert.equal(result['hushåll från v1'], 1, 'bara det friska räknas som migrerat');
  const docs = Object.fromEntries((await exportData(db)).documents.map((d) => [d.path, d.data]));
  assert.equal(docs['households/a-trasigt'].schemaVersion, 1, 'det trasiga stämplas inte');
  assert.equal(docs['households/b-friskt'].schemaVersion, 2);
});

test('saknat steg skriver ingenting och rapporteras', async () => {
  const db = database();
  await seed(db, [{ path: 'households/gammalt', data: { memberUids: ['uid-x'], schemaVersion: 1 } }]);
  const result = await migrateHouseholds(db, 2, v2);
  assert.deepEqual(result.fel, ['gammalt: Migreringssteg saknas för version 1']);
  const docs = Object.fromEntries((await exportData(db)).documents.map((d) => [d.path, d.data]));
  assert.equal(docs['households/gammalt'].schemaVersion, 1);
});
