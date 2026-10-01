// Dataformatets version – speglar Schema.kt i :core (test/schema.test.mjs håller dem lika).
export const CURRENT_VERSION = 2;

/** Version för ett hushåll utan schemaVersion – det första formatet, aldrig "senaste". */
export const FIRST_VERSION = 1;

/** Ett hushålls version ur dess data; saknas den eller är den under den första gäller den första (BCK-5). */
export const versionOf = (household) => {
  const version = household?.schemaVersion;
  return Number.isInteger(version) && version >= FIRST_VERSION ? version : FIRST_VERSION;
};
