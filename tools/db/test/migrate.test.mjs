// migrate.mjs: stämplar hushållet sist och bara när alla steg finns. Endast emulatorn.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { exportData } from '../lib/backup.mjs';
import { migrateHouseholds } from '../lib/migrate.mjs';
import { CURRENT_VERSION } from '../lib/schema.mjs';
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
  await assert.rejects(migrateHouseholds(db, CURRENT_VERSION + 1), /nyare än verktygets/);
});

test('de riktiga stegen: 1 → 2 lämnar dokumenten orörda och stämplar hushållet (POWDER, #23)', async () => {
  const db = database();
  // Fixturen i version 1 (den är i version 2, med ett kosttillskott i form POWDER).
  await seed(db, fixture.documents.map((d) => (d.path.split('/').length === 2 ? { ...d, data: { ...d.data, schemaVersion: 1 } } : d)));
  const before = (await exportData(db)).documents;
  assert.deepEqual(await migrateHouseholds(db, 2), { 'hushåll från v1': 1 });
  const after = Object.fromEntries((await exportData(db)).documents.map((d) => [d.path, d.data]));
  for (const { path, data } of before) {
    assert.deepEqual(after[path], path.split('/').length === 2 ? { ...data, schemaVersion: 2 } : data, path);
  }
});

// Mekaniken med påhittade steg 1 → 2 (current = 2), oberoende av de riktiga stegen.
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

test('ett steg som ändrar dokumentet på plats skrivs ändå', async () => {
  const db = database();
  await seed(db, [
    { path: 'households/gammalt', data: { memberUids: ['uid-x'], schemaVersion: 1, createdAt: new Date('2026-01-01T00:00:00Z') } },
    { path: 'households/gammalt/persons/p1', data: { namn: 'Anna' } },
  ]);
  const inPlace = (collection, doc) => {
    if (collection === 'persons' && 'namn' in doc) { doc.name = doc.namn; delete doc.namn; }
    if (collection === 'households') doc.marginDays = 2;
    return doc;
  };
  await migrateHouseholds(db, 2, { ...v2, steps: { 1: inPlace } });
  assert.deepEqual((await db.doc('households/gammalt/persons/p1').get()).data(), { name: 'Anna' });
  const household = (await db.doc('households/gammalt').get()).data();
  assert.equal(household.marginDays, 2);
  assert.equal(household.schemaVersion, 2);
});

test('ett oförändrat hushållsdokument får bara ny version – en ändring under körningen står kvar', async () => {
  const real = database();
  await seed(real, [
    { path: 'households/gammalt', data: { name: 'Gammalt', memberUids: ['uid-x'], marginDays: 2, schemaVersion: 1 } },
    { path: 'households/gammalt/persons/p1', data: { name: 'Anna' } },
  ]);
  // Användaren ändrar marginaldagarna i appen precis innan hushållet stämplas.
  const bind = (target, key) => (typeof target[key] === 'function' ? target[key].bind(target) : target[key]);
  const db = new Proxy(real, {
    get(target, key) {
      if (key !== 'doc') return bind(target, key);
      return (path) => {
        const ref = target.doc(path);
        if (path !== 'households/gammalt') return ref;
        return new Proxy(ref, {
          get(r, method) {
            if (method !== 'set' && method !== 'update') return bind(r, method);
            return async (...args) => {
              await real.doc(path).update({ marginDays: 5 });
              return r[method](...args);
            };
          },
        });
      };
    },
  });

  assert.deepEqual(await migrateHouseholds(db, 2, { ...v2, steps: { 1: (_, doc) => doc } }), { 'hushåll från v1': 1 });

  const household = (await real.doc('households/gammalt').get()).data();
  assert.equal(household.schemaVersion, 2);
  assert.equal(household.marginDays, 5, 'ändringen i appen skrivs inte över med den äldre läsningen');
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
  const result = await migrateHouseholds(db, 2, { ...v2, steps: {} });
  assert.deepEqual(result.fel, ['gammalt: Migreringssteg saknas för version 1']);
  const docs = Object.fromEntries((await exportData(db)).documents.map((d) => [d.path, d.data]));
  assert.equal(docs['households/gammalt'].schemaVersion, 1);
});
