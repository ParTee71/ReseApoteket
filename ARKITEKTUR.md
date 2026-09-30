# Arkitektur – ReseApoteket

> Beslut och struktur för appen. Regler för hur koden skrivs finns i [CLAUDE.md](CLAUDE.md)
> och skills i [`.claude/skills/`](.claude/skills/). Krav finns i [KRAVLISTA.md](KRAVLISTA.md).

Innehåll:
1. [ADR-001: Grundarkitektur (väg C)](#adr-001-grundarkitektur-väg-c)
2. [Moduler och paket](#moduler-och-paket)
3. [Dataflöde](#dataflöde)
4. [Datamodell](#datamodell)
5. [Verifierade API:er](#verifierade-apier)
6. [Byggkonfiguration](#byggkonfiguration)

---

## ADR-001: Grundarkitektur (väg C)

**Status:** beslutad 2026-09-29 (issue #10).

### Kontext

ReseApoteket räknar ut hur mycket av familjens mediciner, kosttillskott och apoteksvaror
som ska packas inför en resa, och låter familjen pricka av det som plockats fram. Den
används av ett hushåll, enbart på Android.

Krav som styr arkitekturen:
- **Google-inloggning.**
- **Databasen ska gå att nå från en Claude-session** (telefon/webb) för felsökning och frågor.
- **Automatisk backup.**
- **Tester på alla nivåer**, men **låg kostnad i GitHub Actions**.
- **Enkelt, stilfullt, lite kul och tydligt gränssnitt.**
- Utvecklas i huvudsak med Claude Code, ofta från telefonen utan Android SDK.

Utgångspunkten var systerprojektet Dagboken (Kotlin, Compose + Material 3, MVVM, Hilt,
Room, DataStore, Firebase Auth, backup till Google Drive, emulatortester i CI).

### Alternativ

| | A: Dagbokens väg rakt av | B: Dagboken uppgraderad | C: B + nyare och roligare |
|---|---|---|---|
| Data | Room + JSON-backup till Drive | Firestore med offline-cache | Firestore med offline-cache |
| Moduler | `:app` | `:core` (JVM) + `:app` | `:core` (JVM) + `:app` |
| UI | Material 3 | Material 3 | **Material 3 Expressive** |
| Navigation | Navigation Compose 2 | Navigation Compose 2 | **Navigation 3** |
| UI-tester | Android-emulator på varje PR | Robolectric i JVM | Robolectric + **Roborazzi-skärmdumpar** i JVM |
| Databas från sessionen | Nej | Ja | Ja |
| CI per PR | ~50 min | < 8 min | < 8 min |
| Risk | Låg | Låg | Medel (nyare API:er) |

Visuell jämförelse av packlistan i B och C: https://claude.ai/artifact/6P25x1LRrkPBcF9BFqzi7p

A föll på att databasen inte går att nå och att CI blir dyr. Mellan B och C valdes **C**:
samma låga risk i data och tester, men ett designspråk som passar kravet "lite kul".

### Beslut

**Behålls från Dagboken** (beprövat):
- Kotlin, Jetpack Compose, MVVM med `StateFlow<UiState>` och sealed events, Hilt (KSP),
  version catalog, kotlinx.serialization, DataStore för inställningar.
- Firebase Auth + Credential Manager (`GetSignInWithGoogleOption`).
- Regler som skills, `CLAUDE.md`, KRAVLISTA.md med stabila ID:n, svenska i UI.
- Release-flödet: `release.yml` via `workflow_dispatch`, signerad APK som GitHub Release.

**1. Cloud Firestore i stället för Room + Drive-backup.**
Firestore nås från sessionen via Admin SDK och ett service account, har inbyggd
offline-cache (`PersistentCacheSettings`), synkar mellan enheter och gör att hushållet
senare kan dela data. Room-migrationer och Dagbokens `BackupJson`/`BackupMapper`-kedja
ersätts av `schemaVersion` och toleranta codecs. Spark-planen (gratis) räcker med god
marginal för ett hushåll.
*Övervägt:* Supabase/Postgres (SQL, men ingen offline-first i Android-SDK:n och ännu en
leverantör), Realtime Database (sämre frågor och regler), Room + Drive (onåbar, en enhet,
mest kod).
*Risk:* leverantörslåsning – mildras av att `tools/db export` ger vanlig JSON.

**2. `:core` (ren Kotlin/JVM) + `:app`.**
Domänmodeller, codecs och beräkningsmotorn ligger i `:core` utan Android- eller
Firebase-beroenden och testas med JUnit på sekunder, även i en session utan Android SDK.

**3. JVM-först-tester med skärmdumpar.**
Compose-UI testas med Robolectric i `testDebugUnitTest`. Roborazzi tar skärmdumpar av
varje delad komponent och skärm; referensbilder checkas in och diffas i PR:en. Android-emulatorn
körs bara på begäran och i release-flödet.

**4. Material 3 Expressive.**
Expressive-tema och -rörelse (fjädrande), och Expressive-komponenter bakom våra delade
komponenter: vågig progress för packningen, split-knapp för "Lägg till", flytande
verktygsrad som toppnivånavigering, formmorfande kryss, flexibel toppbar. Typsnitt Nunito
(rubriker) och Nunito Sans (brödtext). Exakta API-namn och versioner verifieras i #11 och
skrivs in under [Verifierade API:er](#verifierade-apier); experimentella opt-ins hålls i
`ui/theme` och `ui/components`.

**5. Navigation 3.**
Back stacken är vanlig state med typade `@Serializable`-nycklar och en `NavDisplay`.
Enklare att enhetstesta och passar Expressive-övergångar. Om Navigation 3 saknar stabil
release när skelettet byggs används senaste release candidate, och det noteras här.

**6. Backup med `tools/db` i GitHub Actions.**
Ett Node-verktyg med `firebase-admin` exporterar Firestore till JSON varje vecka; filen
krypteras med gpg (AES-256 med integritetskontroll) och `BACKUP_PASSPHRASE` och sparas som
artifact i 90 dagar i det privata repot. Återställning sker
med `import.mjs`. Samma verktyg ger sessionen läsåtkomst.
*Övervägt:* Firestores schemalagda backups kräver Blaze-planen (betalkort) – kan bytas till senare.

**7. CI-budget.**
Tester körs bara när koden de testar ändrats; lint, minifierat release-bygge och
Android-emulatorn körs veckovis, på begäran och vid release. Firebase-emulatorn körs i PR
bara när rules eller `tools/db` ändrats. Mål under 8 minuter per PR.

**8. Enhetligt och utan dubbelkod, maskinkontrollerat.**
En komponent per elementtyp, en ram per interaktionsmönster, en byggsten per sorts
logik. Kontrolleras av en hook direkt vid filändring, av `UiConsistencyTest` (Konsist) och
`cpdCheck` i bygget, och av dokumentkontroller i CI.

**Uttryckligen inte valt:**
- Nytt DI-ramverk (Metro, kotlin-inject) – ingen vinst för appen, bara risk. Hilt behålls.
- Compose Multiplatform/KMP – appen är enbart för Android.
- Firebase Data Connect / Cloud SQL – kräver Blaze och kostar per månad.

### Konsekvenser

- **Inloggning krävs** – security rules bygger på `request.auth.uid`; ingen data utan konto.
- **Hälsodata i molnet** – skyddas av security rules per hushåll, krypterad backup och
  regeln att aldrig logga innehåll (skill `data-privacy-security`).
- **Nyare API:er** – Expressive och Navigation 3 är unga; mildras av "Verifierade
  API:er" nedan och av att opt-ins isoleras.
- **Mindre att kopiera från Dagboken** i UI-lagret; datalagret blir däremot mindre än
  Dagbokens.
- **Nätverk och nyckel** måste ställas in i Claude-miljön för att nå databasen (README).

---

## Moduler och paket

```
ReseApoteket/
├── core/                         ren Kotlin/JVM – inga Android- eller Firebase-beroenden
│   └── se.partee71.reseapoteket.core
│       ├── model/                Person, Regimen, DoseSchedule, FamilyItem, QuantityRule, Trip, PackingItem …
│       ├── engine/               PackingEngine, ReasonFormatter, DoseScheduleFormatter, QuantityRuleFormatter
│       └── schema/               DocCodec + fälthjälpare, codecs per modell, Schema, SchemaMigrator
├── app/                          Android-appen
│   └── se.partee71.reseapoteket
│       ├── data/
│       │   ├── common/           DataError, suspendRunCatching, EntityCollection, CollectionFactory,
│       │   │                     HouseholdScope, SyncStatus, läs- och skrivregler (DocumentRules)
│       │   ├── auth/             AuthRepository (Credential Manager + Firebase Auth)
│       │   ├── firestore/        FirestoreCollection<T>, CollectionTable, Paths, FirestoreSyncStatus,
│       │   │                     Timestamp ↔ Instant, felmappning, FirestoreModule (Hilt)
│       │   ├── household/        HouseholdSession (aktivt hushåll och dess schemaVersion)
│       │   ├── repository/       tunna fasader per domän
│       │   └── datastore/        SettingsStore
│       ├── di/                   övriga Hilt-moduler (Firestore-modulen ligger i data/firestore)
│       ├── navigation/           Navigation 3: nycklar, back stack, Transitions
│       └── ui/
│           ├── theme/            Expressive-tema, AppColors, AppTypography, AppShapes, Spacing
│           ├── components/       delade komponenter och skärmramar, ComponentGallery
│           ├── common/           ListUiState, EditorState, Validator, DataError.toMessage()
│           ├── auth/             inloggning
│           └── persons/ regimens/ familyitems/ trips/ packing/ settings/
├── tools/db/                     Node: query, get, stats, export, import, migrate + lib/ + tester
├── firestore.rules               security rules
└── .github/workflows/            android.yml, instrumented.yml, quality.yml, release.yml, backup.yml
```

Vad som är delat och var det bor: skill `shared-ui-components` (enda källan).

---

## Dataflöde

```
Compose-skärm
  → ViewModel (StateFlow<UiState>, sealed events, Hilt)
    → Repository (interface, domänfrågor)
      → FirestoreCollection<T>   ← DocCodec<T> (:core)
        → Firestore (offline-cache, synk)

Beräkning:  ViewModel → PackingEngine (:core, rena funktioner) → PackingItem-lista → PackingRepository
Backup:     Firestore → tools/db export (Actions, veckovis) → krypterad artifact → tools/db import
Session:    Claude → tools/db query/get/stats (service account, läsroll) → Firestore
```

- Fel mappas till `DataError` en gång, med `suspendRunCatching` i `data/common/` – av
  `FirestoreCollection` för data och `AuthRepository` för inloggning – och visas via
  `DataError.toMessage()`.
- Skrivningar görs med merge och codecens fullständiga fältlista, så att fält från en nyare
  appversion aldrig raderas av en äldre (skill `data-safety-backup`).
- Listskärmar använder `EntityListScreen` + `asListUiState()`, formulär `EntityEditScreen` + `EditorState`.
- Aktivt hushåll finns i `HouseholdSession` och sätts efter inloggning.

---

## Datamodell

Version 1 (`Schema.CURRENT_VERSION = 1`), byggd i #14. Modellerna ligger i
`core/.../model/`, codecs och fälthjälpare i `core/.../schema/`. Regler för ändringar:
skill `data-safety-backup`. Medvetet platt: inget separat medicinregister – varje recept bär
sin medicins uppgifter.

```
households/{hid}
  name, memberUids: [uid], marginDays = 2, schemaVersion = 1, createdAt
households/{hid}/persons/{pid}
  name, emoji, colorHex, sortOrder, archived
households/{hid}/persons/{pid}/regimens/{rid}
  kind: MEDICINE|SUPPLEMENT, name, form: TABLET|CAPSULE|LIQUID|INHALER|DROPS|PATCH|OTHER,
  strength ("500 mg"), unit, packageSize?,
  schedule: { type: DAILY|WEEKDAYS|INTERVAL|AS_NEEDED,
              dosesPerDay?, unitsPerDose?, weekdays?: [1..7], intervalDays?, asNeededQuantity? },
  notes, active, sortOrder, archived
households/{hid}/familyItems/{fid}
  name, category: CREAM|PLASTER|TOOL|OTHER, unit,
  rule: { type: FIXED|PER_TRAVELLER|PER_DAYS, quantity, perDays? },
  notes, active, sortOrder, archived
households/{hid}/trips/{tid}
  name, startDate (yyyy-MM-dd), days, travellerIds: [pid], status: PLANNING|PACKED|DONE,
  createdAt, updatedAt, archived
households/{hid}/trips/{tid}/packingItems/{iid}
  section: MEDICINE|SUPPLEMENT|FAMILY, personId?, sourceType: REGIMEN|FAMILY_ITEM|MANUAL,
  sourceId?, name, unit, suggestedQuantity, quantity, packages?, reason,
  checked, checkedAt?, hidden, sortOrder
```

`unit` är `TABLETT|KAPSEL|ML|DOS|PUFF|ST|TUB` överallt (`ItemUnit`).

| Modell | Codec | Egenskaper |
|---|---|---|
| `Household` (+ `settings: HouseholdSettings`) | `HouseholdCodec` | `Identified` |
| `Person` | `PersonCodec` | `Identified`, `Sortable`, `Archivable` |
| `Regimen` (+ `DoseSchedule`) | `RegimenCodec`, `DoseScheduleCodec` | `Identified`, `Sortable`, `Archivable` |
| `FamilyItem` (+ `QuantityRule`) | `FamilyItemCodec`, `QuantityRuleCodec` | `Identified`, `Sortable`, `Archivable` |
| `Trip` | `TripCodec` | `Identified`, `Archivable` |
| `PackingItem` | `PackingItemCodec` | `Identified`, `Sortable` |

**Lagring och tolerans**
- Dokument-ID:t är `id` och lagras inte som fält. Hushålls-ID:n slumpas på klienten.
- Datum utan tid lagras som ISO-sträng; `createdAt`, `updatedAt` och `checkedAt` som
  Firestore-`Timestamp`, som `:app` översätter till `kotlin.time.Instant` i `FirestoreCollection`.
- Veckodagar lagras som ISO-nummer (1 = måndag … 7 = söndag), sorterade.
- Alla fält har default. Modellens defaults är precis vad ett tomt dokument betyder –
  därför är `schedule`/`rule` som default `Unknown`: en saknad dosering ger aldrig en gissad mängd.
- `encode` skriver alla kända fält, även `null`; nästlade objekt skriver alla varianters fält.
- En okänd `schedule`/`rule`-typ – eller ett värde som inte ens är ett objekt – blir
  `Unknown(raw)` och skrivs tillbaka oförändrat.
- Ett hushåll utan `schemaVersion` har det första formatet (`Schema.FIRST_VERSION`).
- Ett okänt enum-värde tolkas som default. Därför kräver **ett nytt enum-värde eller en ny
  variant en höjd `schemaVersion`** – då skriver äldre appar ingenting (skill `data-safety-backup`).

---

## Verifierade API:er

Material 3 Expressive och Navigation 3 är nya; här står de artefakter, versioner och
API-namn som faktiskt kompilerats och renderats i projektet. Beviset är
`app/src/test/kotlin/se/partee71/reseapoteket/VerifiedApisTest.kt`, som anropar varje API
nedan – faller det efter en versionsuppdatering har ett API bytt namn. Skills länkar hit.
Använd inget API som inte står här – lägg till det här och i `VerifiedApisTest` i samma PR
som det börjar användas.

**Material 3 Expressive kräver alfaversion.** I den stabila `material3:1.4.0` (Compose BOM
2026.09.00) är alla Expressive-API:er `internal`. Därför pinnas `material3` till
`1.5.0-alpha29` i `libs.versions.toml` (övriga Compose-artefakter följer BOM:en). Byts till
stabil när en stabil version med publika Expressive-API:er släpps. Alla Expressive-API:er
kräver `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`, som bara får stå i `ui/theme` och
`ui/components` (och i tester) – kontrolleras av scopet `[expressive]` i `ui-forbidden.txt`.

| Behov | API (paket.Klass / funktion) | Artefakt:version | Stabil/experimentell | Verifierad |
|---|---|---|---|---|
| Expressive-tema | `androidx.compose.material3.MaterialExpressiveTheme` | `material3:1.5.0-alpha29` | experimentell | 2026-09-30 |
| Expressive-rörelse | `androidx.compose.material3.MotionScheme.expressive()` | `material3:1.5.0-alpha29` | experimentell | 2026-09-30 |
| Ljust Expressive-färgschema | `androidx.compose.material3.expressiveLightColorScheme()` | `material3:1.5.0-alpha29` | experimentell | 2026-09-30 |
| Mörkt färgschema | `androidx.compose.material3.darkColorScheme()` – Expressive saknar mörk motsvarighet; `AppColors` definierar båda scheman med samma roller | `material3:1.5.0-alpha29` | stabil | 2026-09-30 |
| Vågig linjär progress | `androidx.compose.material3.LinearWavyProgressIndicator(progress = { … })` | `material3:1.5.0-alpha29` | experimentell | 2026-09-30 |
| Split-knapp | `androidx.compose.material3.SplitButtonLayout` + `SplitButtonDefaults.LeadingButton` / `TrailingButton(checked, onCheckedChange)` | `material3:1.5.0-alpha29` | experimentell | 2026-09-30 |
| Knappgrupp | `androidx.compose.material3.ButtonGroup(overflowIndicator) { clickableItem(…) }` | `material3:1.5.0-alpha29` | experimentell | 2026-09-30 |
| Flytande verktygsrad | `androidx.compose.material3.HorizontalFloatingToolbar(expanded)` | `material3:1.5.0-alpha29` | experimentell | 2026-09-30 |
| Laddningsindikator | `androidx.compose.material3.LoadingIndicator()` | `material3:1.5.0-alpha29` | experimentell | 2026-09-30 |
| Flexibel toppbar | `androidx.compose.material3.LargeFlexibleTopAppBar(title)` | `material3:1.5.0-alpha29` | experimentell | 2026-09-30 |
| Formmorf för kryss | `androidx.graphics.shapes.Morph(start, end)` med `androidx.compose.material3.MaterialShapes` (t.ex. `Square` → `Cookie9Sided`) och `Morph.toPath(progress)` | `graphics-shapes:1.0.1` (via material3), `material3:1.5.0-alpha29` | experimentell | 2026-09-30 |
| Navigation-nyckel | `androidx.navigation3.runtime.NavKey` + `@Serializable` | `navigation3-runtime:1.2.0` | stabil | 2026-09-30 |
| Back stack | `androidx.navigation3.runtime.rememberNavBackStack(vararg NavKey)` | `navigation3-runtime:1.2.0` | stabil | 2026-09-30 |
| Navigationsvisning | `androidx.navigation3.ui.NavDisplay(backStack, onBack, entryDecorators, entryProvider)` | `navigation3-ui:1.2.0` | stabil | 2026-09-30 |
| Poster | `androidx.navigation3.runtime.entryProvider { entry<K> { … } }` | `navigation3-runtime:1.2.0` | stabil | 2026-09-30 |
| Sparat tillstånd per post | `androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator()` | `navigation3-runtime:1.2.0` | stabil | 2026-09-30 |
| ViewModel per nav-post | `androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator()` | `lifecycle-viewmodel-navigation3:2.11.0` | stabil | 2026-09-30 |

---

## Byggkonfiguration

Beslutad och uppmätt i #11.

| Vad | Värde | Kommentar |
|---|---|---|
| minSdk / targetSdk | 30 / 35 | `app/build.gradle.kts` |
| compileSdk | **37** | Compose BOM 2026.09.00 (även stabila `material-ripple`/`foundation`) kräver 37 i AAR-metadata; issuets 36 räckte inte |
| Gradle | 9.8.0 | wrapper |
| AGP | 9.4.1 | inbyggt Kotlin-stöd och nya DSL:en – `org.jetbrains.kotlin.android` används inte |
| Kotlin | 2.4.20 | versionen styrs av `kotlin-jvm`-pluginen i rotens `plugins {}` |
| KSP / Hilt | 2.3.12 / 2.60.1 | KAPT används inte |
| Compose BOM | 2026.09.00 | `material3` pinnad separat, se [Verifierade API:er](#verifierade-apier) |
| Java | 17 | `sourceCompatibility`/`targetCompatibility` och Kotlins `jvmTarget`; ingen toolchain, så en nyare JDK (t.ex. 21) fungerar för att bygga |
| Robolectric / Roborazzi / Konsist | 4.17 / 1.76.0 / 0.17.3 | Robolectric-SDK och skärmmått i `app/src/test/resources/robolectric.properties` |
| PMD (CPD) | 7.28.0 | se `cpdCheck` nedan |

**Gemensam byggkonfiguration** står på ett ställe: rotens `build.gradle.kts` (`subprojects {}`):
Java 17, Kotlins `jvmTarget`, testtimeout (4 min per testtask, inom PR-budgeten) och testloggning
(`STARTED`-rader, så att ett test som hänger syns som sista rad utan resultat). Med två moduler räcker det;
`build-logic/` införs om fler moduler kommer. Modulfilerna innehåller bara det som är unikt.

**`default_web_client_id` utan `google-services.json`:** saknas filen (PR-bygge, session)
läggs `app/src/authStub/res` till i debug-källorna och ger en stubbsträng, så att
inloggningskoden kompilerar; inloggning misslyckas då vid körning. Finns filen appliceras
`google-services`-pluginen i stället och stubben används inte.

**`cpdCheck`** (rotprojektet, kopplad till `check`): PMD CPD för Kotlin över `core/src`,
`app/src/main`, `app/src/test`, `app/src/sharedTest` och `app/src/androidTest`, utom `*Preview.kt`. Körs via PMD:s CLI (`JavaExec`),
eftersom Gradle-pluginen `de.aaschmid.cpd` inte fungerar med Gradle 9. Tröskel **80 tokens**:
skelettet ger inga träffar, och ett kopierat block på ~10 rader (t.ex. en funktion med en
`when`/`map`-kedja) fångas. Rapport i `build/reports/cpd/cpd.txt` och i felmeddelandet.

**Skärmdumpar:** katalogen anges bara i `roborazzi.outputDir` (`app/build.gradle.kts`); med
`roborazzi.record.filePathStrategy` i `gradle.properties` är filnamnen i testerna relativa till den.

**Svenska testnamn** kräver UTF-8-locale för att Gradle ska kunna skriva testrapporten
(`LANG=C.UTF-8`, som i GitHub Actions). Lokalt med annan locale: `export LC_ALL=C.UTF-8`.

**Uppmätt byggtid** (session, 4 kärnor, kall build cache men varma beroenden):
`./gradlew :core:test :app:testDebugUnitTest :app:verifyRoborazziDebug cpdCheck
:app:compileDebugKotlin` tar **51 s**. Kontrollerna i regel 4 och 2 kostar tillsammans
~15 s: `cpdCheck` ~3 s, `UiConsistencyTest` (Konsist) ~1 s, skärmdumpstestet ~1,5 s och
första Robolectric-testet ~10 s (laddar Android-SDK:n en gång per körning) – under
budgetens 1 minut.
