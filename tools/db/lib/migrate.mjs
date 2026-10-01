// Migreringssteg – spegling av SchemaMigrator i :core. Nyckel = versionen steget migrerar
// från; varje steg är idempotent och tar (samling, dokument) → dokument. Ett nytt steg läggs
// till här och i SchemaMigrator i samma PR.
import { isDeepStrictEqual } from 'node:util';
import { CURRENT_VERSION, versionOf } from './schema.mjs';
import { collectionOf, householdIds, walkHousehold } from './walk.mjs';

export const STEPS = {
  // 1 → 2: bara ett nytt enum-värde (POWDER) – inga befintliga dokument ändras.
  1: (_collection, doc) => doc,
};

/**
 * Migrerar hushållen till version [to]: alla dokument lyfts, och hushållets schemaVersion höjs
 * först när alla är skrivna. Ger antal migrerade hushåll per version de lyftes från, och under
 * `fel` de hushåll som inte gick att migrera (de andra migreras ändå). [current] låter testerna
 * visa mekaniken med en påhittad nyare version.
 */
export async function migrateHouseholds(db, to, { household, dryRun = false, steps = STEPS, current = CURRENT_VERSION } = {}) {
  if (!Number.isInteger(to) || to < 1) throw new Error(`Ogiltig version: ${to}`);
  if (to > current) throw new Error(`Version ${to} är nyare än verktygets ${current}`);
  const result = {};
  for (const id of household ? [household] : await householdIds(db)) {
    try {
      await migrateHousehold(db, id, to, { dryRun, steps, result });
    } catch (error) {
      // Ett trasigt hushåll rapporteras utan att stoppa de andra; körningen fallerar sedan
      // (migrate.mjs) så att felet inte går obemärkt. Meddelandet är ID:n, aldrig innehåll.
      result.fel = [...(result.fel ?? []), `${id}: ${error.message}`];
    }
  }
  return result;
}

async function migrateHousehold(db, id, to, { dryRun, steps, result }) {
  const docs = [];
  for await (const doc of walkHousehold(db, id)) docs.push(doc);
  // Utan hushållsdokument finns ingen version att utgå från: hoppa över och rapportera,
  // i stället för att stoppa migreringen av alla andra hushåll.
  if (docs[0]?.path !== `households/${id}`) {
    result['hoppade över (saknar hushållsdokument)'] = (result['hoppade över (saknar hushållsdokument)'] ?? 0) + 1;
    return;
  }
  const from = versionOf(docs[0].data);
  if (from >= to) return;
  for (let v = from; v < to; v++) if (!steps[v]) throw new Error(`Migreringssteg saknas för version ${v}`);
  const count = () => {
    result[`hushåll från v${from}`] = (result[`hushåll från v${from}`] ?? 0) + 1;
  };
  if (dryRun) return count();
  const migrated = docs.map(({ path, data }) => {
    let doc = data;
    for (let v = from; v < to; v++) doc = steps[v](collectionOf(path), doc);
    return { path, data: doc };
  });
  // Bara dokument som stegen ändrat skrivs – ett oförändrat dokument skrivs aldrig över med en
  // äldre läsning (en användare kan ha sparat under tiden). Hushållet stämplas sist: avbryts
  // körningen läses resten om vid nästa försök (idempotent).
  for (const [i, { path, data }] of migrated.entries()) {
    if (i > 0 && !isDeepStrictEqual(data, docs[i].data)) await db.doc(path).set(data);
  }
  await db.doc(migrated[0].path).set({ ...migrated[0].data, schemaVersion: to });
  count();
}
