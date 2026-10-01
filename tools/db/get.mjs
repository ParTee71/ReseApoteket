#!/usr/bin/env node
// Visar ett dokument som JSON (skill db-access). Läser bara.
import { openFirestore } from './lib/admin.mjs';
import { runCli } from './lib/cli.mjs';
import { getDocument } from './lib/query.mjs';

await runCli({
  usage: `Användning: node tools/db/get.mjs <sökväg> [--household <hid>]

<sökväg> är full (households/<hid>/trips/<tid>) eller relativ hushållet (trips/<tid>).`,
  positionals: true,
  options: { household: { type: 'string' } },
  main: async ({ household }, [docPath]) => {
    if (!docPath) throw new Error('<sökväg> saknas');
    console.log(JSON.stringify(await getDocument(openFirestore(), docPath, { household }), null, 2));
  },
});
