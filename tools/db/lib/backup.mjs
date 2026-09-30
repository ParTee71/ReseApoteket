// Export och import – logiken bakom export.mjs, import.mjs och veckobackupen (BCK-1–3).
import { CURRENT_VERSION, versionOf } from './schema.mjs';
import { fromJson, toJson } from './serialize.mjs';
import { collectionOf, householdIds, walkHousehold } from './walk.mjs';

/** Firestores gräns för antal skrivningar i en batch. */
export const MAX_BATCH = 500;

/** `{ exportedAt, schemaVersion, documents: [{ path, data }] }` för ett eller alla hushåll. */
export async function exportData(db, { household, now = new Date() } = {}) {
  const ids = household ? [household] : await householdIds(db);
  const documents = [];
  let schemaVersion = 0;
  for (const id of ids) {
    const before = documents.length;
    for await (const doc of walkHousehold(db, id)) {
      if (doc.path === `households/${id}`) schemaVersion = Math.max(schemaVersion, versionOf(doc.data));
      documents.push({ path: doc.path, data: toJson(doc.data) });
    }
    if (household && documents.length === before) throw new Error(`Hushållet ${id} finns inte`);
  }
  return { exportedAt: now.toISOString(), schemaVersion: schemaVersion || CURRENT_VERSION, documents };
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

/** Kontrollerar en export innan något skrivs; ger dokumenten som ska importeras. */
export function validateExport(data, { household } = {}) {
  if (!data || !Array.isArray(data.documents)) throw new Error('Filen är inte en export från tools/db (documents saknas)');
  if (!Number.isInteger(data.schemaVersion)) throw new Error('Filen saknar schemaVersion');
  if (data.schemaVersion > CURRENT_VERSION) {
    throw new Error(`Filen har schemaVersion ${data.schemaVersion}, verktyget förstår bara ${CURRENT_VERSION} – uppdatera tools/db först`);
  }
  const documents = household
    ? data.documents.filter((d) => d.path === `households/${household}` || d.path.startsWith(`households/${household}/`))
    : data.documents;
  for (const { path } of documents) {
    if (!collectionOf(path)) throw new Error(`Okänd sökväg i filen: ${path}`);
  }
  if (household && documents.length === 0) throw new Error(`Hushållet ${household} finns inte i filen`);
  return documents;
}

/**
 * Skriver dokumenten exakt som i filen (set utan merge), i batchar om högst 500.
 * `dryRun` skriver ingenting. Ger antal dokument per samling.
 */
export async function importData(db, data, { household, dryRun = false } = {}) {
  const documents = validateExport(data, { household });
  if (!dryRun) {
    for (let i = 0; i < documents.length; i += MAX_BATCH) {
      const batch = db.batch();
      for (const { path, data: doc } of documents.slice(i, i + MAX_BATCH)) batch.set(db.doc(path), fromJson(doc));
      await batch.commit();
    }
  }
  return summarize(documents);
}
