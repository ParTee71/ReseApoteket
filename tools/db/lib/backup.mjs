// Export och import – logiken bakom export.mjs, import.mjs och veckobackupen (BCK-1–3, 6–8).
import { CURRENT_VERSION, versionOf } from './schema.mjs';
import { fromJson, toJson } from './serialize.mjs';
import { collectionOf, householdIds, householdOf, isHouseholdDoc, walkHousehold } from './walk.mjs';

/** Firestores gräns för antal skrivningar i en batch. */
export const MAX_BATCH = 500;

/** Kör [convert] och lägger till dokumentets sökväg i ett fel – aldrig värdet. */
function withPath(path, convert) {
  try {
    return convert();
  } catch (error) {
    throw new Error(`${path}: ${error.message}`);
  }
}

/**
 * `{ exportedAt, schemaVersion, documents: [{ path, data }] }` för ett eller alla hushåll.
 * `schemaVersion` är den högsta bland hushållen (informativ – importen kontrollerar varje hushåll).
 */
export async function exportData(db, { household, now = new Date() } = {}) {
  const ids = household ? [household] : await householdIds(db);
  const documents = [];
  let schemaVersion = null;
  for (const id of ids) {
    const before = documents.length;
    for await (const doc of walkHousehold(db, id)) {
      if (isHouseholdDoc(doc.path)) schemaVersion = Math.max(schemaVersion ?? 0, versionOf(doc.data));
      documents.push({ path: doc.path, data: withPath(doc.path, () => toJson(doc.data)) });
    }
    if (household && documents.length === before) throw new Error(`Hushållet ${id} finns inte`);
  }
  return { exportedAt: now.toISOString(), schemaVersion: schemaVersion ?? CURRENT_VERSION, documents };
}

/**
 * Läser en exportfil. Ett parsningsfel ger ett fast meddelande: Nodes eget meddelande citerar
 * filens innehåll, och det är hälsodata.
 */
export function parseExport(text) {
  try {
    return JSON.parse(text);
  } catch {
    throw new Error('Filen är inte giltig JSON (innehållet visas inte)');
  }
}

/** Antal dokument per samling – det som visas i stället för innehåll (hälsodata). */
export function summarize(documents) {
  const counts = {};
  for (const { path } of documents) {
    const name = collectionOf(path);
    counts[name] = (counts[name] ?? 0) + 1;
  }
  return counts;
}

/**
 * Kontrollerar och översätter hela filen innan något skrivs: format, kända sökvägar, varje
 * hushålls schemaVersion och varje dokuments värden. Ger `[{ path, data }]` redo att skrivas.
 */
export function prepareImport(data, { household } = {}) {
  if (!data || !Array.isArray(data.documents)) throw new Error('Filen är inte en export från tools/db (documents saknas)');
  const documents = household
    ? data.documents.filter((d) => householdOf(d.path ?? '') === household)
    : data.documents;
  if (household && documents.length === 0) throw new Error(`Hushållet ${household} finns inte i filen`);
  return documents.map(({ path, data: doc }) => {
    if (typeof path !== 'string' || !collectionOf(path)) throw new Error(`Okänd sökväg i filen: ${path}`);
    if (doc === null || typeof doc !== 'object' || Array.isArray(doc)) throw new Error(`${path}: data saknas`);
    if (isHouseholdDoc(path) && versionOf(doc) > CURRENT_VERSION) {
      throw new Error(`${path} har schemaVersion ${versionOf(doc)}, verktyget förstår bara ${CURRENT_VERSION} – uppdatera tools/db först`);
    }
    return { path, data: withPath(path, () => fromJson(doc)) };
  });
}

/** Dokument som finns i databasen under filens hushåll men inte i filen. */
async function extraDocuments(db, documents) {
  const inFile = new Set(documents.map((d) => d.path));
  const extra = [];
  for (const id of new Set(documents.map((d) => householdOf(d.path)))) {
    for await (const doc of walkHousehold(db, id)) if (!inFile.has(doc.path)) extra.push(doc.path);
  }
  return extra;
}

async function commitInBatches(db, operations) {
  for (let i = 0; i < operations.length; i += MAX_BATCH) {
    const batch = db.batch();
    for (const op of operations.slice(i, i + MAX_BATCH)) op(batch);
    await batch.commit();
  }
}

/**
 * Skriver dokumenten exakt som i filen (set utan merge), i batchar om högst 500 – först när
 * hela filen godkänts. `replace` tar dessutom bort dokument under filens hushåll som inte finns
 * i filen, så att hushållet blir exakt som i backupen. `dryRun` skriver ingenting (med
 * `replace` läses databasen för att räkna det som skulle tas bort).
 * Ger `{ written: antal per samling, removed: antal per samling }`.
 */
export async function importData(db, data, { household, dryRun = false, replace = false } = {}) {
  const documents = prepareImport(data, { household });
  const removed = replace ? await extraDocuments(db, documents) : [];
  if (!dryRun) {
    await commitInBatches(db, [
      ...removed.map((path) => (batch) => batch.delete(db.doc(path))),
      ...documents.map(({ path, data: doc }) => (batch) => batch.set(db.doc(path), doc)),
    ]);
  }
  return { written: summarize(documents), removed: summarize(removed.map((path) => ({ path }))) };
}
