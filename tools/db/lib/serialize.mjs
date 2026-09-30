// Firestore-värden ⇄ JSON, för både export och import (skill data-safety-backup).
// Timestamp ⇄ { "__ts": "…Z" } med alla nio decimaler, så att rundturen blir exakt.
// Obs: JavaScript skiljer inte heltal från flyttal, så 2.0 kommer tillbaka som 2 –
// codecarna i :core läser båda. Tal som JSON inte kan bära exakt (heltal över 2^53, NaN,
// oändligt) stoppar exporten i stället för att tyst ändras.
import { Timestamp } from 'firebase-admin/firestore';

const TS = '__ts';

function tsToIso(ts) {
  const whole = new Date(ts.seconds * 1000).toISOString().slice(0, 19);
  return `${whole}.${String(ts.nanoseconds).padStart(9, '0')}Z`;
}

function isoToTs(iso) {
  const match = /^(.{19})(?:\.(\d{1,9}))?Z$/.exec(iso);
  if (!match) throw new Error(`Ogiltig tidsstämpel i exporten: ${iso}`);
  const seconds = Date.parse(`${match[1]}Z`) / 1000;
  return new Timestamp(seconds, Number((match[2] ?? '').padEnd(9, '0')));
}

const isPlainObject = (v) => v !== null && typeof v === 'object' && Object.getPrototypeOf(v) === Object.prototype;

/** Firestore-data → JSON-säkert värde. Okända typer stoppar exporten i stället för att tappas. */
export function toJson(value) {
  if (typeof value === 'number') {
    if (!Number.isFinite(value) || (Number.isInteger(value) && !Number.isSafeInteger(value))) {
      throw new Error(`Talet ${value} kan inte exporteras exakt`);
    }
    return value;
  }
  if (value === null || ['string', 'boolean'].includes(typeof value)) return value;
  if (value instanceof Timestamp) return { [TS]: tsToIso(value) };
  if (Array.isArray(value)) return value.map(toJson);
  if (isPlainObject(value)) return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, toJson(v)]));
  throw new Error(`Värdetypen ${value?.constructor?.name ?? typeof value} stöds inte i exporten`);
}

/** JSON → Firestore-data. */
export function fromJson(value) {
  if (Array.isArray(value)) return value.map(fromJson);
  if (isPlainObject(value)) {
    const keys = Object.keys(value);
    if (keys.length === 1 && keys[0] === TS) return isoToTs(value[TS]);
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, fromJson(v)]));
  }
  return value;
}
