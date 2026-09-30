// Går igenom ett hushåll enligt collections.mjs – används av export och migrate (och stats
// och query i #18). En undersamling som inte står i listan stoppar genomgången: den skulle
// annars aldrig backas upp.
import { COLLECTIONS } from './collections.mjs';

const segments = (p) => p.split('/');

/** Undersamlingarna direkt under ett dokument i samlingen `template`. */
const childrenOf = (template) =>
  COLLECTIONS.filter((c) => c.path.startsWith(`${template}/`) && segments(c.path).length === segments(template).length + 2);

/** Dokumentet (om det har egen data) och allt under det, enligt samlingen `template`. */
async function* walkDocument(docRef, template, snap) {
  if (snap.exists) yield { path: docRef.path, data: snap.data() };
  const children = childrenOf(template);
  const known = new Set(children.map((c) => c.name));
  for (const sub of await docRef.listCollections()) {
    if (!known.has(sub.id)) {
      throw new Error(`Okänd samling ${sub.path} – lägg till den i tools/db/lib/collections.mjs (skill data-safety-backup)`);
    }
  }
  for (const child of children) yield* walkCollection(docRef.collection(child.name), child.path);
}

async function* walkCollection(ref, template) {
  // listDocuments ger även "saknade" föräldrar – dokument utan egen data men med undersamlingar.
  for (const docRef of await ref.listDocuments()) yield* walkDocument(docRef, template, await docRef.get());
}

/**
 * Alla dokument i ett hushåll: hushållsdokumentet först, sedan undersamlingarna. Saknas
 * hushållsdokumentet tas undersamlingarna ändå med – ett trasigt hushåll ska inte fälla backupen.
 */
export async function* walkHousehold(db, householdId) {
  const ref = db.doc(`households/${householdId}`);
  yield* walkDocument(ref, 'households', await ref.get());
}

/** ID:n för alla hushåll (för export av allt). */
export async function householdIds(db) {
  return (await db.collection('households').listDocuments()).map((d) => d.id);
}

/** Samlingens namn för en dokumentsökväg, eller null om den inte finns i listan. */
export function collectionOf(docPath) {
  const parts = segments(docPath);
  if (parts.length % 2 !== 0) return null;
  const match = COLLECTIONS.find((c) => {
    const t = segments(c.path);
    return t.length === parts.length - 1 && t.every((s, i) => s.startsWith('{') || s === parts[i]);
  });
  return match?.name ?? null;
}
