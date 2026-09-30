// Kontroller före import och serialisering – ingen emulator behövs.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GeoPoint, Timestamp } from 'firebase-admin/firestore';
import { validateExport } from '../lib/backup.mjs';
import { CURRENT_VERSION } from '../lib/schema.mjs';
import { fromJson, toJson } from '../lib/serialize.mjs';

const household = { path: 'households/h', data: { schemaVersion: 1 } };

test('import vägrar en fil med nyare schemaVersion än verktyget (BCK-5)', () => {
  assert.throws(
    () => validateExport({ schemaVersion: CURRENT_VERSION + 1, documents: [household] }),
    /nyare|förstår bara/,
  );
});

test('import vägrar okända sökvägar och filer som inte är exporter', () => {
  assert.throws(() => validateExport({ schemaVersion: 1, documents: [{ path: 'users/x', data: {} }] }), /Okänd sökväg/);
  assert.throws(() => validateExport({ schemaVersion: 1 }), /documents saknas/);
  assert.throws(() => validateExport({ documents: [household] }), /schemaVersion/);
  assert.throws(() => validateExport({ schemaVersion: 1, documents: [household] }, { household: 'annat' }), /finns inte i filen/);
});

test('tidsstämplar serialiseras med alla nio decimaler och tillbaka', () => {
  const ts = new Timestamp(1_790_000_000, 123_456_789);
  const json = toJson({ at: ts, list: [ts], n: 0.25, empty: null });
  assert.deepEqual(json, { at: { __ts: '2026-09-21T14:13:20.123456789Z' }, list: [{ __ts: '2026-09-21T14:13:20.123456789Z' }], n: 0.25, empty: null });
  const back = fromJson(json);
  assert.ok(back.at.isEqual(ts));
  assert.ok(back.list[0].isEqual(ts));
});

test('okända Firestore-typer stoppar exporten', () => {
  assert.throws(() => toJson({ ref: new Uint8Array([1]) }), /stöds inte/);
  assert.throws(() => toJson({ plats: new GeoPoint(59.3, 18.1) }), /stöds inte/);
});

test('tal som JSON inte kan bära exakt stoppar exporten i stället för att ändras', () => {
  assert.throws(() => toJson({ n: 2 ** 53 + 2 }), /kan inte exporteras exakt/);
  assert.throws(() => toJson({ n: Number.NaN }), /kan inte exporteras exakt/);
  assert.throws(() => toJson({ n: Infinity }), /kan inte exporteras exakt/);
  assert.equal(toJson(Number.MAX_SAFE_INTEGER), Number.MAX_SAFE_INTEGER);
});
