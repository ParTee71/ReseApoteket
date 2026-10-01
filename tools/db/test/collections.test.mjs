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

test('varje samling har en egen regel under hushållet, och inget annat är öppet', () => {
  const rules = read('firestore.rules');
  assert.match(rules, /match \/households\/\{hid\} \{/);
  // Ett jokertecken skulle släppa in samlingar som stoppar backupen (BCK-8).
  assert.doesNotMatch(rules, /match \/\{\w+\}\//, 'ingen match på godtyckliga samlingar');
  const matched = [...rules.matchAll(/match \/(\w+)\/\{\w+\}/g)].map((m) => m[1]).filter((name) => name !== 'databases');
  for (const c of COLLECTIONS.filter((c) => c.name !== 'households')) {
    assert.ok(c.path.startsWith('households/{hid}/'), `${c.path} ligger inte under ett hushåll`);
    assert.ok(matched.includes(c.name), `${c.name} saknar regel i firestore.rules`);
  }
  assert.deepEqual(matched.sort(), COLLECTIONS.map((c) => c.name).sort(), 'rules och collections.mjs har samma samlingar');
});
