---
name: firestore-data-layer
description: ReseApotekets datalager — Firestore via en generisk FirestoreCollection<T> och DocCodec<T>, tunna repositories, offline-cache, security rules och FakeCollection i tester. Ersätter Dagbokens Room-skills. Ladda denna ALLTID när du rör data/firestore, data/repository, en codec, en ny samling, Paths, HouseholdSession, firestore.rules, offline/synk eller repository-tester. Trigger-ord: Firestore, databas, repository, samling, collection, dokument, codec, DocCodec, FirestoreCollection, Paths, offline, synk, synkas, cache, snapshots, hasPendingWrites, security rules, firestore.rules, memberUids, hushåll, household, FakeCollection, CollectionContract, DataError.
---

# Datalagret: Firestore

**Kärnregel:** ingen samling har egen Firestore-kod. All Firestore-åtkomst går genom
`FirestoreCollection<T>` i `app/.../data/firestore/`. Repositories är tunna fasader.
Modeller och codecs ligger i `:core` utan Android- eller Firebase-beroenden.

Tabellen över alla delade byggstenar står i skill `shared-ui-components` (enda källan).
Datakedjan och schemaregler står i skill `data-safety-backup`.

## Dokumentstruktur

Fem samlingar under `households/{hid}`: `persons`, `persons/{pid}/regimens`,
`familyItems`, `trips` och `trips/{tid}/packingItems`. Struktur och fält står **bara** i
`ARKITEKTUR.md` → "Datamodell" (fylls i av #14). Sökvägarna finns i `Paths` och speglas i
`tools/db/lib/collections.mjs` – ett test håller dem lika.

## Lager

```
ViewModel
  → XRepository (interface, data/repository)          domänfrågor, inget Firestore
    → FirestoreXRepository                            delegerar
      → FirestoreCollection<X>(Paths.x(hid), XCodec)  enda Firestore-koden
        → FirebaseFirestore (PersistentCacheSettings)
```

### `DocCodec<T>` (`:core`)
```kotlin
interface DocCodec<T> {
    fun encode(value: T): Map<String, Any?>
    fun decode(id: String, map: Map<String, Any?>): T
}
```
Byggs enbart av fälthjälparna (`map.string`, `map.int`, `map.enum`, `map.localDate`,
`map.instant`, `map.nested`, `map.stringList`). Toleranta mot saknade och okända fält.

### `FirestoreCollection<T>` (`data/firestore/`)
| Medlem | Beteende |
|---|---|
| `observe(): Flow<List<T>>` | `snapshots()` → `codec.decode`; sorterat via `Sortable` om tillämpligt |
| `observe(id): Flow<T?>` | ett dokument |
| `get(id): Result<T?>` | engångsläsning |
| `upsert(item): Result<Unit>` | `set(…, SetOptions.merge())` med codecens fullständiga fältlista; okända fält från nyare appversioner bevaras (skill `data-safety-backup`); `updatedAt` sätts här |
| `delete(id): Result<Unit>` | permanent radering |
| `setArchived(id, archived): Result<Unit>` | för `Archivable` |
| `batch { … }: Result<Unit>` | flera skrivningar atomärt, delas i bitar om 500 |
| `newId(): String` | klientgenererat ID – fungerar offline |
| `syncing: Flow<Boolean>` | `hasPendingWrites` → appens enda "synkas…"-indikator |

- Alla skrivningar går via `suspendRunCatching` (i `data/common/`) och mappar
  `FirebaseFirestoreException` → `DataError` **här, en gång**. `DataError` (`Offline`,
  `PermissionDenied`, `Cancelled`, `Unknown`) ligger i `data/common/` och används även av
  `AuthRepository`. UI visar texten via `DataError.toMessage()`.
- Läser aktivt hushåll från `HouseholdSession`; utan hushåll emitteras inget.
- `Timestamp` ↔ `Instant` översätts här; `:core` ser aldrig Firebase-typer.

### Repositories (`data/repository/`)
Interface + implementation som bara delegerar och uttrycker domänfrågor, t.ex.
`regimensFor(personIds)`, `replacePackingItems(tripId, items)` (via `batch`). Ingen
`FirebaseFirestore`, ingen felmappning, ingen codec-logik.

### DI
Hilt-modulen `FirestoreModule` ligger i **`data/firestore/`** (inte i `di/`), eftersom
`FirebaseFirestore` bara får förekomma där. Den ger `FirebaseFirestore` med
`PersistentCacheSettings` (100 MB). `CollectionFactory` skapar alla
`FirestoreCollection`-instanser – sökvägar på ett ställe. Övriga Hilt-moduler ligger i `di/`.

## Offline

- Firestore-cachen är källan för UI; väntar aldrig på nätverket.
- Skrivningar lyckas lokalt direkt och synkas senare; `syncing` visar läget.
- ID:n skapas på klienten med `newId()`.

## Security rules

- Allt under `households/{hid}` kräver `request.auth.uid in memberUids`.
- Hushåll får skapas av den som står i `memberUids`; `memberUids` får inte tömmas och
  ändras inte från appen förrän delning byggs (FUT-2); hushåll raderas inte från appen.
- Hushålls-ID:n slumpas på klienten (`newId()`), så att ingen kan förhandsregistrera ett
  förutsägbart ID. `schemaVersion` skrivs som heltal – annars nekar reglerna.
- Ny samling = ny regel + rules-test i samma PR.
- Deploy sker manuellt (`npx --prefix tools/db firebase deploy --only firestore:rules`, README → Firebase-setup), aldrig från CI i en PR.
- **Fallgrop:** i `rules_version = '2'` matchar `{document=**}` även noll segment. Under
  `households/{hid}` används därför `match /{collection}/{document=**}` – annars gäller
  underreglerna också hushållsdokumentet och kringgår create/update/delete-reglerna.
  Rules-testet "hushåll kan inte raderas" fångar det.

## Tester

| Test | Var |
|---|---|
| Codec-rundtur och tolerans | `core/src/test/.../schema/` (hjälparna i skill `testing-strategy`) |
| `CollectionContract` mot `FakeCollection` | `app/src/test/.../data/` (JVM, varje PR när `app/` ändras) |
| `CollectionContract` mot riktig `FirestoreCollection` + Firebase-emulatorn | `app/src/androidTest/.../data/` (instrumenttest: på begäran och i release) |
| Rules-test | `tools/db/test/rules.test.mjs` |
| `Paths` = `collections.mjs` = rules | `tools/db/test/collections.test.mjs` |

Repository-tester: bygg repositoryt med `FakeCollection` och testa bara domänfrågorna.
`FakeCollection` emulerar `upsert` med merge (okända fält bevaras, `null` tömmer), så att
JVM-testerna i varje PR fångar samma sak som instrumenttestet mot riktig Firestore.

## Kostnad (Spark-planen)

- Lyssna inte på stora samlingar utan `where`/`limit`. Alla hushållets samlingar är små,
  men packlistor per resa ska lyssnas på per resa, inte alla på en gång.
- Stäng lyssnare när skärmen lämnas (`WhileSubscribed(5_000)` i ViewModel).

## Anti-mönster

- `FirebaseFirestore`, `CollectionReference` eller `DocumentReference` utanför `data/firestore/`,
  även i `di/` (fälls av `UiConsistencyTest` och hooken `regel4-check`).
- Egen felmappning eller egen `try/catch` runt Firestore i ett repository.
- Egen parsning av en `Map` i stället för en codec.
- En `Fake*Repository` med egen in-memory-logik i stället för `FakeCollection`.
- Serverdatum i `:core`, eller `Timestamp` i en modell.
