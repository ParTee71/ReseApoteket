// Gemensam testhjälpare: tester i tools/db/test körs BARA mot Firebase-emulatorn med ett
// demo-projekt – aldrig mot den riktiga databasen (skill data-safety-backup, BCK-4).
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';

export const PROJECT_ID = 'demo-reseapoteket';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');

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
    firestore: { ...emulatorHost(), rules: readFileSync(path.join(repoRoot, 'firestore.rules'), 'utf8') },
  });
}
