// Läsverktygen (skill db-access) skriver aldrig – kontrollerat i källkoden, så att en framtida
// ändring fångas i CI. Ingen emulator behövs.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { readRepoFile } from './helpers/repo.mjs';

const toolsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const READ_ONLY = ['query.mjs', 'get.mjs', 'stats.mjs', 'lib/query.mjs'];

test('query, get och stats innehåller inga skrivande anrop', () => {
  for (const file of READ_ONLY) {
    const source = readRepoFile(`tools/db/${file}`);
    for (const call of ['.set(', '.update(', '.delete(', '.create(', '.batch(', 'recursiveDelete', 'importData', 'migrateHouseholds', 'write: true']) {
      assert.ok(!source.includes(call), `${file} innehåller ${call}`);
    }
  }
});

test('utan nyckel: svenskt fel som hänvisar till README, exitkod 1 och inget nätverksanrop', () => {
  const env = { PATH: process.env.PATH };
  const result = spawnSync(process.execPath, [path.join(toolsDir, 'stats.mjs')], { encoding: 'utf8', env, timeout: 20_000 });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /^Fel: FIREBASE_SERVICE_ACCOUNT saknas.*README → Utveckling via Claude/);
  assert.match(result.stderr, /Klistra aldrig in nyckeln i chatten/);
});
