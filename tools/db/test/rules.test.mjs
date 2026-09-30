// Security rules (skill firestore-data-layer). Körs bara mot emulatorn – se helpers/emulator.mjs.
import { after, before, beforeEach, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where } from 'firebase/firestore';
import { rulesTestEnvironment } from './helpers/emulator.mjs';

const HID = 'hushall-1';
const household = { memberUids: ['anna'], schemaVersion: 1, name: 'Familjen Test' };

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

test('allt utanför households är stängt', async () => {
  await assertFails(getDoc(doc(db('anna'), 'users', 'anna')));
  await assertFails(setDoc(doc(db('anna'), 'annat', 'x'), { a: 1 }));
});
