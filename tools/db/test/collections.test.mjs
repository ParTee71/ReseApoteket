// Samlingslistan i tools/db = Paths.kt i appen = security rules (skill data-safety-backup).
// En samling som saknas här backas aldrig upp. Behöver ingen emulator.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { COLLECTIONS } from '../lib/collections.mjs';
import { readRepoFile as read } from './helpers/repo.mjs';
const pathsKt = read('app/src/main/kotlin/se/partee71/reseapoteket/data/firestore/Paths.kt');

/** Platshållare ({hid}, $personId …) jämförs bara till sin position. */
const normalize = (p) => p.replace(/\$\{?\w+\}?|\{\w+\}/g, '{}');

test('samlingsnamnen är desamma som konstanterna i Paths.kt', () => {
  const constants = [...pathsKt.matchAll(/const val \w+ = "([^"]+)"/g)].map((m) => m[1]);
  assert.deepEqual([...constants].sort(), COLLECTIONS.map((c) => c.name).sort());
});

test('sökvägarna är desamma som i Paths.kt', () => {
  const functions = [...pathsKt.matchAll(/fun \w+\([^)]*\) = "([^"]+)"/g)].map((m) => normalize(m[1]));
  // household(hid) pekar på ett dokument, inte en samling.
  const collectionPaths = functions.filter((p) => p !== 'households/{}');
  const expected = COLLECTIONS.filter((c) => c.name !== 'households').map((c) => normalize(c.path));
  assert.deepEqual([...collectionPaths].sort(), [...expected].sort());
  for (const c of COLLECTIONS) assert.ok(c.path.endsWith(c.name), `${c.path} ska sluta med ${c.name}`);
});

test('varje samling skyddas av security rules för hushållet', () => {
  const rules = read('firestore.rules');
  assert.match(rules, /match \/households\/\{hid\} \{/);
  assert.match(rules, /match \/\{collection\}\/\{document=\*\*\} \{\s*allow read, write: if isMember\(hid\);/);
  for (const c of COLLECTIONS.filter((c) => c.name !== 'households')) {
    assert.ok(c.path.startsWith('households/{hid}/'), `${c.path} ligger inte under ett hushåll`);
  }
});
