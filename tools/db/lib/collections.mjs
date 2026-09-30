// Alla Firestore-samlingar – enda listan som export, import och query går igenom
// (skill data-safety-backup). Speglar Paths.kt i appen; test/collections.test.mjs håller dem lika.
// {hid} = hushåll, {pid} = person, {tid} = resa.

export const COLLECTIONS = [
  { name: 'households', path: 'households' },
  { name: 'persons', path: 'households/{hid}/persons' },
  { name: 'regimens', path: 'households/{hid}/persons/{pid}/regimens' },
  { name: 'familyItems', path: 'households/{hid}/familyItems' },
  { name: 'trips', path: 'households/{hid}/trips' },
  { name: 'packingItems', path: 'households/{hid}/trips/{tid}/packingItems' },
];
