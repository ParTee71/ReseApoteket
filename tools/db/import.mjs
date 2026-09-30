#!/usr/bin/env node
// Återställer en export (BCK-3, skill db-access). Skriver – kör alltid --dry-run först.
import { readFileSync } from 'node:fs';
import { openFirestore } from './lib/admin.mjs';
import { importData } from './lib/backup.mjs';
import { printSummary, runCli } from './lib/cli.mjs';

await runCli({
  usage: `Användning: node tools/db/import.mjs --in <fil.json> [--household <hid>] [--dry-run]

Skriver dokumenten i filen exakt som de är (befintliga dokument med samma sökväg ersätts).
Vägrar om filens schemaVersion är nyare än verktyget. --dry-run visar bara antal per samling.
Kör när appen inte används – importen skriver över dokument som ändrats under tiden.`,
  options: { in: { type: 'string' }, household: { type: 'string' }, 'dry-run': { type: 'boolean' } },
  main: async ({ in: file, household, 'dry-run': dryRun }) => {
    if (!file) throw new Error('--in saknas');
    const data = JSON.parse(readFileSync(file, 'utf8'));
    const counts = await importData(dryRun ? null : openFirestore({ write: true }), data, { household, dryRun });
    printSummary(dryRun ? 'Torrkörning – skulle skriva:' : 'Importerade:', counts);
  },
});
