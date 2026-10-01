#!/usr/bin/env node
// Återställer en export (BCK-3, BCK-6, skill db-access). Skriver – kör alltid --dry-run först.
import { readFileSync } from 'node:fs';
import { openFirestore } from './lib/admin.mjs';
import { importData, parseExport } from './lib/backup.mjs';
import { printSummary, runCli } from './lib/cli.mjs';

await runCli({
  usage: `Användning: node tools/db/import.mjs --in <fil.json> [--household <hid>] [--replace] [--dry-run]

Skriver dokumenten i filen exakt som de är (befintliga dokument med samma sökväg ersätts),
men först när hela filen kontrollerats. --replace tar dessutom bort dokument under filens
hushåll som inte finns i filen, så att hushållet blir exakt som i backupen.
Vägrar hushåll med nyare schemaVersion än verktyget. --dry-run visar bara antal per samling.
Kör när appen inte används – importen skriver över dokument som ändrats under tiden.`,
  options: {
    in: { type: 'string' },
    household: { type: 'string' },
    replace: { type: 'boolean' },
    'dry-run': { type: 'boolean' },
  },
  main: async ({ in: file, household, replace, 'dry-run': dryRun }) => {
    if (!file) throw new Error('--in saknas');
    const data = parseExport(readFileSync(file, 'utf8'));
    // Torrkörning utan --replace behöver ingen databas; med --replace läses den bara.
    const db = dryRun && !replace ? null : openFirestore({ write: !dryRun });
    const { written, removed } = await importData(db, data, { household, dryRun, replace });
    printSummary(dryRun ? 'Torrkörning – skulle skriva:' : 'Skrev:', written);
    if (replace) printSummary(dryRun ? 'Skulle ta bort:' : 'Tog bort:', removed);
  },
});
