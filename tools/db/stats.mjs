#!/usr/bin/env node
// Hälsokoll: antal dokument per samling och schemaVersion per hushåll (skill db-access). Läser bara.
import { openFirestore } from './lib/admin.mjs';
import { printSummary, runCli } from './lib/cli.mjs';
import { householdStats } from './lib/query.mjs';

await runCli({
  usage: `Användning: node tools/db/stats.mjs [--household <hid>]

Visar per hushåll: schemaVersion och antal dokument per samling. Inget innehåll.`,
  options: { household: { type: 'string' } },
  main: async ({ household }) => {
    for (const { household: id, schemaVersion, counts } of await householdStats(openFirestore(), { household })) {
      printSummary(`Hushåll ${id} (schemaVersion ${schemaVersion ?? 'saknar hushållsdokument'}):`, counts);
    }
  },
});
