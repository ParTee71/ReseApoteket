// Migreringssteg – spegling av SchemaMigrator i :core. Nyckel = versionen steget migrerar
// från; varje steg är idempotent och tar (samling, dokument) → dokument. Tom tills formatet
// ändras första gången; ett nytt steg läggs till här och i SchemaMigrator i samma PR.
import { CURRENT_VERSION, versionOf } from './schema.mjs';
import { collectionOf, householdIds, walkHousehold } from './walk.mjs';

export const STEPS = {};

/**
 * Migrerar hushållen till version [to]: alla dokument lyfts, och hushållets schemaVersion höjs
 * först när alla är skrivna. Ger antal migrerade hushåll per version de lyftes från.
 */
export async function migrateHouseholds(db, to, { household, dryRun = false, steps = STEPS } = {}) {
  if (!Number.isInteger(to) || to < 1) throw new Error(`Ogiltig version: ${to}`);
  if (to > CURRENT_VERSION) throw new Error(`Version ${to} är nyare än verktygets ${CURRENT_VERSION}`);
  const result = {};
  for (const id of household ? [household] : await householdIds(db)) {
    const docs = [];
    for await (const doc of walkHousehold(db, id)) docs.push(doc);
    // Utan hushållsdokument finns ingen version att utgå från: hoppa över och rapportera,
    // i stället för att stoppa migreringen av alla andra hushåll.
    if (docs[0]?.path !== `households/${id}`) {
      result['hoppade över (saknar hushållsdokument)'] = (result['hoppade över (saknar hushållsdokument)'] ?? 0) + 1;
      continue;
    }
    const from = versionOf(docs[0].data);
    if (from >= to) continue;
    for (let v = from; v < to; v++) if (!steps[v]) throw new Error(`Migreringssteg saknas för version ${v}`);
    result[`hushåll från v${from}`] = (result[`hushåll från v${from}`] ?? 0) + 1;
    if (dryRun) continue;
    const migrated = docs.map(({ path, data }) => {
      let doc = data;
      for (let v = from; v < to; v++) doc = steps[v](collectionOf(path), doc);
      return { path, data: doc };
    });
    // Hushållet stämplas sist: avbryts körningen läses resten om vid nästa försök (idempotent).
    for (const { path, data } of migrated.slice(1)) await db.doc(path).set(data);
    await db.doc(migrated[0].path).set({ ...migrated[0].data, schemaVersion: to });
  }
  return result;
}
