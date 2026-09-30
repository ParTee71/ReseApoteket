// Gemensam kommandoradstolkning för skripten i tools/db: --help och svenska felmeddelanden.
import { parseArgs } from 'node:util';

/**
 * Tolkar argumenten enligt [options] (node:util parseArgs) och kör [main]. Fel skrivs som
 * en rad utan stackspår och ger exitkod 1 – inga dokument eller nycklar i utskriften.
 */
export async function runCli({ usage, options, main }) {
  let values;
  try {
    ({ values } = parseArgs({ options: { ...options, help: { type: 'boolean', short: 'h' } }, strict: true }));
  } catch (error) {
    console.error(`Fel: ${error.message}\n\n${usage}`);
    process.exit(1);
  }
  if (values.help) {
    console.log(usage);
    return;
  }
  try {
    await main(values);
  } catch (error) {
    console.error(`Fel: ${error.message}`);
    process.exit(1);
  }
}

/** Skriver antal dokument per samling. */
export function printSummary(title, counts) {
  console.log(title);
  for (const [name, count] of Object.entries(counts)) console.log(`  ${name}: ${count}`);
}
