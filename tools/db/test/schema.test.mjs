// Schemaversionen finns på två ställen: Schema.kt i appen och lib/schema.mjs här. De ska vara lika.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { CURRENT_VERSION, FIRST_VERSION } from '../lib/schema.mjs';
import { STEPS } from '../lib/migrate.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const schemaKt = readFileSync(path.join(repoRoot, 'core/src/main/kotlin/se/partee71/reseapoteket/core/schema/Schema.kt'), 'utf8');
const migratorKt = readFileSync(path.join(repoRoot, 'core/src/main/kotlin/se/partee71/reseapoteket/core/schema/SchemaMigrator.kt'), 'utf8');
const constant = (name) => Number(schemaKt.match(new RegExp(`const val ${name} = (\\d+)`))[1]);

test('CURRENT_VERSION och FIRST_VERSION är desamma som i Schema.kt', () => {
  assert.equal(CURRENT_VERSION, constant('CURRENT_VERSION'));
  assert.equal(FIRST_VERSION, constant('FIRST_VERSION'));
});

test('migreringsstegen speglar SchemaMigrator', () => {
  const kotlinSteps = migratorKt.includes('steps: Map<Int, Step> = emptyMap()') ? [] : null;
  assert.ok(kotlinSteps, 'SchemaMigrator har fått steg – spegla dem i lib/migrate.mjs och uppdatera testet');
  assert.deepEqual(Object.keys(STEPS), kotlinSteps.map(String));
});
