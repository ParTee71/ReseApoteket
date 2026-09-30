// Gemensam testhjälpare: tester i tools/db/test körs BARA mot Firebase-emulatorn med ett
// demo-projekt – aldrig mot den riktiga databasen (skill data-safety-backup, BCK-4).
import { after, before, beforeEach } from 'node:test';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { openFirestore } from '../../lib/admin.mjs';
import { fromJson } from '../../lib/serialize.mjs';
import { readRepoFile } from './repo.mjs';

export const PROJECT_ID = 'demo-reseapoteket';


/** Värd och port för emulatorn; avbryter om FIRESTORE_EMULATOR_HOST saknas. */
export function emulatorHost() {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  if (!host) {
    throw new Error(
      'FIRESTORE_EMULATOR_HOST saknas – testerna körs bara mot Firebase-emulatorn. Kör från repots rot:\n' +
        `  npx --prefix tools/db firebase emulators:exec --only firestore --project ${PROJECT_ID} "npm --prefix tools/db test"`,
    );
  }
  const [hostname, port] = host.split(':');
  return { host: hostname, port: Number(port) };
}

/** Testmiljö för rules-tester med repots firestore.rules. */
export function rulesTestEnvironment() {
  return initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { ...emulatorHost(), rules: readRepoFile('firestore.rules') },
  });
}

/** Admin-Firestore mot emulatorn – alltid i testläge (bara emulator och demo-projekt). */
export function adminFirestore() {
  emulatorHost();
  return openFirestore({ test: true, projectId: PROJECT_ID });
}

/** Den syntetiska fixturen (test/fixtures/household.json), delad av testerna. */
export const fixture = JSON.parse(readRepoFile('tools/db/test/fixtures/household.json'));

/** Ett andra hushåll, för tester av att bara det valda hushållet rörs. */
export const otherHousehold = { path: 'households/annat', data: { name: 'Annat', memberUids: ['uid-x'], schemaVersion: 1 } };

/** Lägger fixturens dokument direkt i databasen – utan import, så att exporten testas fristående. */
export async function seed(db, documents = fixture.documents) {
  for (const { path: docPath, data } of documents) await db.doc(docPath).set(fromJson(data));
}

/** Raderar alla hushåll och allt under dem. */
export async function clearHouseholds(db) {
  await db.recursiveDelete(db.collection('households'));
}

/**
 * Admin-Firestore med tomma hushåll före och efter varje test i filen. Ger en funktion som
 * returnerar databasen (den skapas i `before`).
 */
export function useCleanEmulator() {
  let db;
  before(() => {
    db = adminFirestore();
  });
  beforeEach(() => clearHouseholds(db));
  after(() => clearHouseholds(db));
  return () => db;
}
