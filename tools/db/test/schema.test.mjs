// Schemaversionen finns på två ställen: Schema.kt i appen och lib/schema.mjs här. De ska vara lika.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CURRENT_VERSION, FIRST_VERSION } from '../lib/schema.mjs';
import { STEPS } from '../lib/migrate.mjs';
import { readRepoFile } from './helpers/repo.mjs';

const schemaDir = 'core/src/main/kotlin/se/partee71/reseapoteket/core/schema';
const schemaKt = readRepoFile(`${schemaDir}/Schema.kt`);
const migratorKt = readRepoFile(`${schemaDir}/SchemaMigrator.kt`);
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
