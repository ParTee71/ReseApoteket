#!/usr/bin/env node
// Exporterar hushållsdata till JSON (BCK-1, skill db-access). Läser bara.
import { writeFileSync } from 'node:fs';
import { openFirestore } from './lib/admin.mjs';
import { exportData, summarize } from './lib/backup.mjs';
import { printSummary, runCli } from './lib/cli.mjs';

await runCli({
  usage: `Användning: node tools/db/export.mjs --out <fil.json> [--household <hid>]

Exporterar ett hushåll (eller alla) till JSON. Filen innehåller hälsodata: lägg den i
tools/db/ (git-ignorerad) och radera den när den inte behövs. En tom databas ger fel.`,
  options: { out: { type: 'string' }, household: { type: 'string' } },
  main: async ({ out, household }) => {
    if (!out) throw new Error('--out saknas');
    const data = await exportData(openFirestore(), { household });
    // Fel projekt, fel nyckel eller saknad behörighet ser ut som en tom databas – det får
    // aldrig bli en grön backup.
    if (data.documents.length === 0) throw new Error('Inga dokument hittades – kontrollera nyckeln och projektet');
    writeFileSync(out, JSON.stringify(data, null, 2));
    printSummary(`Exporterade till ${out} (schemaVersion ${data.schemaVersion}):`, summarize(data.documents));
  },
});
