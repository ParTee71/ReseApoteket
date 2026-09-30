#!/usr/bin/env node
// Listar dokument i en samling (skill db-access). Läser bara.
import { openFirestore } from './lib/admin.mjs';
import { printTable, runCli } from './lib/cli.mjs';
import { parseWhere, queryCollection } from './lib/query.mjs';

await runCli({
  usage: `Användning: node tools/db/query.mjs <samling> [--household <hid>] [--where fält=värde]
       [--limit 50] [--fields a,b] [--json]

<samling> tolkas relativt hushållet: persons, persons/<pid>/regimens, familyItems, trips,
trips/<tid>/packingItems. "households" listar alla hushåll nyckeln ser. Finns bara ett
hushåll väljs det automatiskt. --where kan anges flera gånger (likhet).`,
  positionals: true,
  options: {
    household: { type: 'string' },
    where: { type: 'string', multiple: true },
    limit: { type: 'string', default: '50' },
    fields: { type: 'string' },
    json: { type: 'boolean' },
  },
  main: async ({ household, where = [], limit, fields, json }, [collection]) => {
    if (!collection) throw new Error('<samling> saknas');
    const rows = await queryCollection(openFirestore(), collection, {
      household,
      where: where.map(parseWhere),
      limit: Number(limit),
      fields: fields?.split(',').map((f) => f.trim()).filter(Boolean),
    });
    if (json) console.log(JSON.stringify(rows, null, 2));
    else printTable(rows);
  },
});
