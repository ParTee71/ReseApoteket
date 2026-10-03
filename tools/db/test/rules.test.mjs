// Security rules (skill firestore-data-layer). Körs bara mot emulatorn – se helpers/emulator.mjs.
import { after, before, beforeEach, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { readFileSync } from 'node:fs';
import { Timestamp, collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where } from 'firebase/firestore';
import { CURRENT_VERSION } from '../lib/schema.mjs';
import { rulesTestEnvironment } from './helpers/emulator.mjs';

const HID = 'hushall-1';
const household = { memberUids: ['anna'], schemaVersion: 1, name: 'Familjen Test' };

/** Exportens JSON (`{ __ts }`-tidsstämplar) som klientens värden, som appen skriver dem. */
const toClient = (value) => {
  if (Array.isArray(value)) return value.map(toClient);
  if (value && typeof value === 'object') {
    if (typeof value.__ts === 'string' && Object.keys(value).length === 1) return Timestamp.fromDate(new Date(value.__ts));
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, toClient(v)]));
  }
  return value;
};

let env;
const db = (uid) => (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).firestore();

before(async () => {
  env = await rulesTestEnvironment();
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'households', HID), household);
    await setDoc(doc(ctx.firestore(), 'households', HID, 'persons', 'p1'), { name: 'Anna' });
  });
});

test('medlem läser och skriver hushållet och allt under det', async () => {
  await assertSucceeds(getDoc(doc(db('anna'), 'households', HID)));
  await assertSucceeds(updateDoc(doc(db('anna'), 'households', HID), { name: 'Resegänget' }));
  await assertSucceeds(getDoc(doc(db('anna'), 'households', HID, 'persons', 'p1')));
  await assertSucceeds(setDoc(doc(db('anna'), 'households', HID, 'persons', 'p2', 'regimens', 'r1'), { name: 'Levaxin' }));
  await assertSucceeds(deleteDoc(doc(db('anna'), 'households', HID, 'persons', 'p1')));
});

test('icke-medlem nekas läsning och skrivning', async () => {
  await assertFails(getDoc(doc(db('bertil'), 'households', HID)));
  await assertFails(updateDoc(doc(db('bertil'), 'households', HID), { name: 'Kapat' }));
  await assertFails(getDoc(doc(db('bertil'), 'households', HID, 'persons', 'p1')));
  await assertFails(setDoc(doc(db('bertil'), 'households', HID, 'persons', 'p9'), { name: 'X' }));
});

test('inloggningens fråga: egna hushåll kan listas, andras och alla inte', async () => {
  const households = (uid) => collection(db(uid), 'households');
  const mine = await assertSucceeds(getDocs(query(households('anna'), where('memberUids', 'array-contains', 'anna'))));
  if (mine.size !== 1) throw new Error(`väntade 1 hushåll, fick ${mine.size}`);
  await assertFails(getDocs(query(households('bertil'), where('memberUids', 'array-contains', 'anna'))));
  await assertFails(getDocs(households('anna')));
  await assertFails(getDocs(query(collection(db(null), 'households'), where('memberUids', 'array-contains', 'anna'))));
});

test('oautentiserad nekas allt', async () => {
  await assertFails(getDoc(doc(db(null), 'households', HID)));
  await assertFails(getDoc(doc(db(null), 'households', HID, 'persons', 'p1')));
  await assertFails(setDoc(doc(db(null), 'households', 'nytt'), { memberUids: ['x'], schemaVersion: 1 }));
});

test('hushåll kan skapas bara med skaparen som enda medlem', async () => {
  await assertSucceeds(setDoc(doc(db('cecilia'), 'households', 'h2'), { memberUids: ['cecilia'], schemaVersion: 1 }));
  await assertFails(setDoc(doc(db('cecilia'), 'households', 'h3'), { memberUids: ['david'], schemaVersion: 1 }));
  // Annars kunde cecilia lägga davids uid i ett eget, äldre hushåll som david sedan väljer.
  await assertFails(setDoc(doc(db('cecilia'), 'households', 'h6'), { memberUids: ['cecilia', 'david'], schemaVersion: 1 }));
  await assertFails(setDoc(doc(db('cecilia'), 'households', 'h7'), { memberUids: ['david', 'cecilia'], schemaVersion: 1 }));
});

test('ett befintligt hushåll kan inte skrivas över av någon annan', async () => {
  await assertFails(setDoc(doc(db('bertil'), 'households', HID), { memberUids: ['bertil'], schemaVersion: 1 }));
});

test('memberUids kan inte ändras från appen (delning kommer med FUT-2)', async () => {
  await assertFails(updateDoc(doc(db('anna'), 'households', HID), { memberUids: ['anna', 'bertil'] }));
  await assertFails(updateDoc(doc(db('anna'), 'households', HID), { memberUids: ['bertil'] }));
  await assertSucceeds(updateDoc(doc(db('anna'), 'households', HID), { memberUids: ['anna'], name: 'Samma medlemmar' }));
});

test('hushåll kan inte raderas, inte ens av en medlem', async () => {
  await assertFails(deleteDoc(doc(db('anna'), 'households', HID)));
});

test('memberUids kan inte tömmas och schemaVersion måste vara ett heltal', async () => {
  await assertFails(updateDoc(doc(db('anna'), 'households', HID), { memberUids: [] }));
  await assertFails(updateDoc(doc(db('anna'), 'households', HID), { schemaVersion: '1' }));
  await assertFails(setDoc(doc(db('cecilia'), 'households', 'h4'), { memberUids: [], schemaVersion: 1 }));
  await assertFails(setDoc(doc(db('cecilia'), 'households', 'h5'), { memberUids: ['cecilia'] }));
});

test('bara samlingarna i DAT-1 kan skrivas – ingen okänd samling eller djup (BCK-8)', async () => {
  const anna = db('anna');
  await assertFails(setDoc(doc(anna, 'households', HID, 'okand', 'x'), { a: 1 }));
  await assertFails(setDoc(doc(anna, 'households', HID, 'persons', 'p1', 'notes', 'y'), { a: 1 }));
  await assertFails(setDoc(doc(anna, 'households', HID, 'trips', 't1', 'packingItems', 'i1', 'extra', 'z'), { a: 1 }));
  await assertFails(getDoc(doc(anna, 'households', HID, 'okand', 'x')));
  await assertSucceeds(setDoc(doc(anna, 'households', HID, 'familyItems', 'f1'), { name: 'Solkräm', rule: { type: 'FIXED', quantity: 1 } }));
  await assertSucceeds(setDoc(doc(anna, 'households', HID, 'trips', 't1'), { name: 'Kreta', days: 7, travellerIds: ['p1'] }));
  await assertSucceeds(setDoc(doc(anna, 'households', HID, 'trips', 't1', 'packingItems', 'i1'), { name: 'Solkräm', quantity: 2, checked: false }));
});

test('kända fält med fel typ nekas, men null, saknade och okända fält är tillåtna (DAT-5)', async () => {
  const anna = db('anna');
  await assertFails(setDoc(doc(anna, 'households', HID, 'persons', 'p2'), { name: 'Bo', sortOrder: 'först' }));
  await assertFails(setDoc(doc(anna, 'households', HID, 'persons', 'p1', 'regimens', 'r1'), { name: 'Levaxin', schedule: 'dagligen' }));
  await assertFails(setDoc(doc(anna, 'households', HID, 'trips', 't1'), { name: 'Kreta', days: 'abc' }));
  await assertFails(setDoc(doc(anna, 'households', HID, 'trips', 't1', 'packingItems', 'i1'), { name: 'X', checked: 'ja' }));
  await assertFails(updateDoc(doc(anna, 'households', HID), { marginDays: 'två' }));
  // Vid create kontrolleras alla fält – även listor, tider och familjeartiklar.
  await assertFails(setDoc(doc(db('cecilia'), 'households', 'h9'), { memberUids: ['cecilia'], schemaVersion: 1, marginDays: 'två' }));
  await assertFails(setDoc(doc(anna, 'households', HID, 'trips', 't2'), { name: 'Kreta', travellerIds: 'p1' }));
  await assertFails(setDoc(doc(anna, 'households', HID, 'trips', 't2'), { name: 'Kreta', createdAt: '2026-10-03' }));
  await assertFails(setDoc(doc(anna, 'households', HID, 'familyItems', 'f2'), { name: 'Solkräm', rule: 'en' }));
  await assertSucceeds(setDoc(doc(anna, 'households', HID, 'persons', 'p2'), { name: 'Bo', emoji: null, framtidaFalt: { a: 1 } }));
  await assertSucceeds(setDoc(doc(anna, 'households', HID, 'persons', 'p1', 'regimens', 'r1'), { name: 'Levaxin', packageSize: null, schedule: { type: 'DAILY' } }));
});

test('schemaVersion är minst 1, kan inte sänkas och inte skruvas upp förbi den högsta (BCK-5)', async () => {
  await assertFails(setDoc(doc(db('cecilia'), 'households', 'h8'), { memberUids: ['cecilia'], schemaVersion: 0 }));
  await assertFails(updateDoc(doc(db('anna'), 'households', HID), { schemaVersion: 0 }));
  const newer = CURRENT_VERSION + 1;
  await assertFails(updateDoc(doc(db('anna'), 'households', HID), { schemaVersion: newer }), 'skulle låsa ute alla medlemmar');
  await assertSucceeds(updateDoc(doc(db('anna'), 'households', HID), { schemaVersion: 1, name: 'Samma version' }));
  // En migrering stämplar hushållet med den högsta versionen som finns.
  await assertSucceeds(updateDoc(doc(db('anna'), 'households', HID), { schemaVersion: CURRENT_VERSION }));
  // Skaparen är enda medlem; ett hushåll från en nyare app får stå kvar i sin version.
  await assertSucceeds(setDoc(doc(db('cecilia'), 'households', 'h9'), { memberUids: ['cecilia'], schemaVersion: newer }));
  await assertSucceeds(updateDoc(doc(db('cecilia'), 'households', 'h9'), { name: 'Nyare', schemaVersion: newer }));
  await assertFails(updateDoc(doc(db('cecilia'), 'households', 'h9'), { schemaVersion: CURRENT_VERSION }), 'sänkt');
});

test('ett lagrat värde med fel typ hindrar inte att andra fält sparas (DAT-6)', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const admin = ctx.firestore();
    await setDoc(doc(admin, 'households', HID, 'persons', 'p1', 'regimens', 'r1'), { name: 'Gammal', schedule: 'dagligen' });
    await setDoc(doc(admin, 'households', 'utan-version'), { memberUids: ['anna'], name: 'Gammalt' });
  });
  const anna = db('anna');
  await assertSucceeds(setDoc(doc(anna, 'households', HID, 'persons', 'p1', 'regimens', 'r1'), { archived: true }, { merge: true }));
  await assertFails(setDoc(doc(anna, 'households', HID, 'persons', 'p1', 'regimens', 'r1'), { schedule: 'varannan' }, { merge: true }));
  // Saknad schemaVersion är den första (BCK-5): appen får stämpla den och spara.
  await assertSucceeds(setDoc(doc(anna, 'households', 'utan-version'), { name: 'Nytt namn', schemaVersion: 1 }, { merge: true }));
});

test('appens dokument i codecens form (alla fält, null och tidsstämplar) godtas', async () => {
  const anna = db('anna');
  const { documents } = JSON.parse(readFileSync(new URL('./fixtures/household.json', import.meta.url), 'utf8'));
  const [first, ...rest] = documents;
  // Fixturens hushåll med Anna som enda medlem, så att hon får skapa det; sedan resten som hon.
  await assertSucceeds(setDoc(doc(anna, first.path), toClient({ ...first.data, memberUids: ['anna'] })));
  for (const { path, data } of rest) await assertSucceeds(setDoc(doc(anna, path), toClient(data)));
});

test('allt utanför households är stängt', async () => {
  await assertFails(getDoc(doc(db('anna'), 'users', 'anna')));
  await assertFails(setDoc(doc(db('anna'), 'annat', 'x'), { a: 1 }));
});
