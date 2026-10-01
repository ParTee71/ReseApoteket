// Dataformatets version – speglar Schema.kt i :core (test/schema.test.mjs håller dem lika).
export const CURRENT_VERSION = 1;

/** Version för ett hushåll utan schemaVersion – det första formatet, aldrig "senaste". */
export const FIRST_VERSION = 1;

/** Ett hushålls version ur dess data. */
export const versionOf = (household) =>
  Number.isInteger(household?.schemaVersion) ? household.schemaVersion : FIRST_VERSION;
