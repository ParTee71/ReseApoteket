// Nycklar från miljön: rå JSON eller base64, rätt projekt, och värdet syns aldrig i ett fel.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseServiceAccount, serviceAccountFor } from '../lib/credentials.mjs';

// Syntetisk nyckel – inget riktigt konto.
const key = { type: 'service_account', project_id: 'reseapoteket', private_key: 'HEMLIG-TESTNYCKEL', client_email: 'x@reseapoteket.iam.gserviceaccount.com' };
const raw = JSON.stringify(key, null, 2);

test('rå JSON och base64 ger samma nyckel', () => {
  assert.deepEqual(parseServiceAccount(raw, 'NYCKEL'), key);
  assert.deepEqual(parseServiceAccount(Buffer.from(raw).toString('base64'), 'NYCKEL'), key);
  assert.deepEqual(parseServiceAccount(`\n  ${raw}\n`, 'NYCKEL'), key, 'blanksteg runt det inklistrade');
});

test('tom, trasig eller fel sorts nyckel stoppar utan att visa värdet', () => {
  assert.throws(() => parseServiceAccount('', 'NYCKEL'), /NYCKEL saknas/);
  for (const bad of ['{"private_key":"HEMLIG-TESTNYCKEL"', JSON.stringify({ ...key, type: 'authorized_user' })]) {
    assert.throws(() => parseServiceAccount(bad, 'NYCKEL'), (error) => {
      assert.match(error.message, /^NYCKEL /);
      assert.doesNotMatch(error.message, /HEMLIG/);
      return true;
    });
  }
});

test('en nyckel för ett annat projekt stoppas – aldrig deploy till fel projekt', () => {
  assert.equal(serviceAccountFor(raw, 'NYCKEL', 'reseapoteket').project_id, 'reseapoteket');
  assert.throws(() => serviceAccountFor(JSON.stringify({ ...key, project_id: 'dagboken-711d2' }), 'NYCKEL', 'reseapoteket'), /dagboken-711d2, inte reseapoteket/);
});
