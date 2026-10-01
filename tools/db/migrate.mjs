#!/usr/bin/env node
// Migrerar hushållens data till en ny schemaVersion (skill data-safety-backup). Speglar
// SchemaMigrator i :core – båda är tomma tills formatet ändras första gången. Skriver.
import { openFirestore } from './lib/admin.mjs';
import { printSummary, runCli } from './lib/cli.mjs';
import { migrateHouseholds } from './lib/migrate.mjs';

await runCli({
  usage: `Användning: node tools/db/migrate.mjs --to <N> [--household <hid>] [--dry-run]

Lyfter varje hushåll under version N steg för steg och stämplar det sedan med N.
Kör när appen inte används – dokument som ändras under körningen kan skrivas över.`,
  options: { to: { type: 'string' }, household: { type: 'string' }, 'dry-run': { type: 'boolean' } },
  main: async ({ to, household, 'dry-run': dryRun }) => {
    if (!to) throw new Error('--to saknas');
    const { fel, ...counts } = await migrateHouseholds(openFirestore({ write: !dryRun }), Number(to), { household, dryRun });
    printSummary(dryRun ? 'Torrkörning – skulle migrera:' : 'Migrerade:', counts);
    if (fel) {
      console.error('Kunde inte migreras:');
      for (const line of fel) console.error(`  ${line}`);
      process.exitCode = 1;
    }
  },
});
