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
    → EntityCollection<X> (data/common)               från CollectionFactory
      → FirestoreCollection<X>                        enda Firestore-koden (data/firestore)
        → FirebaseFirestore (PersistentCacheSettings, 100 MB)
```

### `DocCodec<T>` (`:core`)
Se skill `data-safety-backup` och `ARKITEKTUR.md` → "Datamodell". Byggs enbart av
fälthjälparna; toleranta mot saknade och okända fält.

### `EntityCollection<T>` (`data/common/`) – ett kontrakt, två implementationer
| Medlem | Beteende |
|---|---|
| `observe(): Flow<List<T>>` | från cachen, följer aktivt hushåll (inget hushåll → inget emitteras); sorterat på `sortOrder` för `Sortable` |
| `observe(id): Flow<T?>` | ett dokument, `null` om det saknas |
| `get(id)`, `getAll()` | engångsläsning som `Result` |
| `upsert(item)` | `set(…, merge)` med codecens fullständiga fältlista; okända fält bevaras, `null` tömmer; `updatedAt` sätts, `createdAt` utan värde skrivs inte (`prepareForWrite`) |
| `delete(id)` | permanent radering |
| `setArchived(id, archived)` | ändrar bara `archived` via `update` – ett raderat dokument återuppstår inte |
| `batch(upserts, deletes)` | i bitar om 500 – atomärt inom varje bit, inte över bitar |
| `newId()` | slumpat klient-ID – fungerar offline |

- **Offline först:** en skrivning läggs i cachen och `Result` blir klart direkt – den väntar
  aldrig på servern. Servern svarar senare; väntande skrivningar och sena fel syns i
  `SyncStatus` (`syncing` → "synkas…", `lastWriteError` ligger kvar tills UI:t visat det och
  anropat `clearWriteError()` – ett fel medan appen är i bakgrunden försvinner inte).
- **Skrivskydd, stängt som standard:** är hushållets `schemaVersion` nyare än appen vägras
  skrivningar med `DataError.UpdateRequired`; läsning fungerar. Är versionen för det aktiva
  hushållet ännu okänd väntar skrivningen upp till 5 s och vägras sedan (`Offline`) hellre än
  att en äldre app skriver över nyare data (`writeBlocker` i `data/common/HouseholdScope.kt`).
  Versionen är knuten till hushålls-ID:t, så en gammal version gäller aldrig ett nytt hushåll.
- **Ordning:** `sortForList` – på dokument-ID och sedan `sortOrder`, likadant i riktig och fejkad samling.
- **Läsning:** `readDocument` kör `SchemaMigrator` före `decode`. `Timestamp` ↔ `Instant`
  översätts i `data/firestore/FirestoreValues.kt`, rekursivt; `:core` ser aldrig Firebase-typer.
- **Fel:** `firestoreError` mappar `FirebaseFirestoreException` → `DataError` en gång, via
  `suspendRunCatching`. `DataError` (`Offline`, `PermissionDenied`, `Cancelled`,
  `UpdateRequired`, `SignInRejected`, `NoHousehold`, `NotFound`, `Unknown`) används även av `AuthRepository`. UI visar
  texten via `DataError.toMessage()`.
- **`FakeCollection`** (test) använder samma `prepareForWrite`/`readDocument` och emulerar
  Firestores merge (djup för nästlade maps, heltal som `Long`). `CollectionContract` i
  `app/src/sharedTest` körs mot båda.

### Samlingar och sökvägar
`CollectionTable` (`data/firestore/`) är enda tabellen över samlingarnas namn, codecs och
sökvägar (`Paths`). `FirestoreCollectionFactory` och testernas `FakeCollectionFactory` ärver
den. `tools/db/lib/collections.mjs` speglar `Paths`; `tools/db/test/collections.test.mjs`
jämför dem.

### Repositories (`data/repository/`)
Interface + `Default…Repository`. Enkla samlingar är bara delegering
(`PersonRepository : EntityCollection<Person>`, `… by collections.persons()`); egen kod finns
bara för domänfrågor: `HouseholdRepository.create`/`current`, `RegimenRepository.regimensFor`,
`PackingRepository.replacePackingItems` (via `batch`). Ingen `FirebaseFirestore`, ingen
felmappning, ingen codec-logik.

### Aktivt hushåll
`HouseholdSession` (`data/household/`, implementerar `HouseholdScope`): hushålls-ID:t sparas i
DataStore (`SettingsStore`, som ger standardvärden om filen inte går att läsa),
`schemaVersion` läses direkt ur hushållsdokumentet (`HouseholdVersionSource`; vid fel
emitteras inget, så skrivspärren förblir stängd). Ett äldre hushåll vars väg till appens
version inte ändrar några dokument (`SchemaMigrator.canStamp`) stämplas av sessionen med
`HouseholdVersionSource.stamp` (bara fältet `schemaVersion`, en gång per körning). `select(id)` återvänder först när
sessionen visar det nya hushållet, så nästa skrivning hamnar rätt.
`selection` är det sparade valet som flöde och emitterar först när DataStore lästs.
Hushållen en användare är medlem i hämtas med `HouseholdDirectory` (fråga
`memberUids array-contains uid` mot servern) – används av `EnsureHouseholdUseCase` vid inloggning.

### DI
`FirestoreModule` ligger i **`data/firestore/`** (inte i `di/`), eftersom `FirebaseFirestore`
bara får förekomma där. Den ger `FirebaseFirestore` med `PersistentCacheSettings` och binder
`CollectionFactory`, `SyncStatus`, `HouseholdVersionSource` och `HouseholdDirectory`. Övriga moduler i `di/`
(`AppModule`: app-scope, klocka, DataStore; `RepositoryModule`).

## Offline

- Firestore-cachen är källan för UI; väntar aldrig på nätverket.
- Skrivningar lyckas lokalt direkt och synkas senare; `syncing` visar läget.
- ID:n skapas på klienten med `newId()`.

## Security rules

- Allt under `households/{hid}` kräver `request.auth.uid in memberUids`. Hushållsdokumentet
  läses via `resource.data.memberUids` (inte `get()`), så att inloggningens fråga
  `memberUids array-contains <egen uid>` godkänns; en fråga utan villkoret nekas.
- Hushåll får skapas av den som står i `memberUids`; `memberUids` får inte tömmas och
  ändras inte från appen förrän delning byggs (FUT-2); hushåll raderas inte från appen.
- Hushålls-ID:n slumpas på klienten (`newId()`), så att ingen kan förhandsregistrera ett
  förutsägbart ID. `schemaVersion` skrivs som heltal – annars nekar reglerna.
- Bara samlingarna i DAT-1 har regler; allt annat under hushållet nekas. Kända fält
  typkontrolleras (`optInt`, `optString` …) – men bara de fält skrivningen ändrar, så att ett
  redan lagrat felaktigt värde (t.ex. okänd dosering som skrivs tillbaka, DAT-6) inte låser
  dokumentet. Saknat eller `null` är tillåtet, okända fält får finnas. `schemaVersion` är
  minst 1 (saknad lagrad version räknas som 1), kan inte sänkas och höjs högst till
  `maxSchemaVersion()`; vid create finns inget tak – skaparen är enda medlem och kan bara
  låsa ute sig själv. Rules-testet "appens dokument i codecens form" skriver fixturen genom
  klientens SDK och fångar drift mellan codecs och rules.
- Ny samling = ny `match` med `valid…`-funktion + rules-test i samma PR.
- Deploy sker manuellt (`npx --prefix tools/db firebase deploy --only firestore:rules`, README → Firebase-setup), aldrig från CI i en PR.
- **Fallgrop:** i `rules_version = '2'` matchar `{document=**}` även noll segment – det skulle
  ge underreglerna makt över hushållsdokumentet. Använd uttryckliga `match /<samling>/{id}`,
  aldrig jokertecken. Rules-testet "hushåll kan inte raderas" fångar det.

## Tester

| Test | Var |
|---|---|
| Codec-rundtur och tolerans | `core/src/test/.../schema/` (hjälparna i skill `testing-strategy`) |
| `CollectionContract` (i `app/src/sharedTest`) mot `FakeCollection` | `FakeCollectionContractTest`, JVM, varje PR när `app/` ändras |
| `CollectionContract` mot riktig `FirestoreCollection` + Firebase-emulatorn (firestore + auth, rules aktiva) | `FirestoreCollectionContractTest` i `app/src/androidTest` (instrumenttest: på begäran och i release) |
| Offline först och hushållets version mot riktig Firestore | `FirestoreOfflineTest` i `app/src/androidTest` (`disableNetwork` → skrivning lyckas, syns lokalt, synkas efter `enableNetwork`) |
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
