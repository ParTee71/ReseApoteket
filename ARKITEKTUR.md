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
│       │   ├── common/           DataError, suspendRunCatching – felmappning en gång
│       │   ├── auth/             AuthRepository (Credential Manager + Firebase Auth)
│       │   ├── firestore/        FirestoreCollection<T>, Paths, CollectionFactory, FirestoreModule (Hilt)
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

*Fylls i av #14.* Planerad struktur (se #14 för fält):

```
households/{hid}
households/{hid}/persons/{pid}
households/{hid}/persons/{pid}/regimens/{rid}
households/{hid}/familyItems/{fid}
households/{hid}/trips/{tid}
households/{hid}/trips/{tid}/packingItems/{iid}
```

---

## Verifierade API:er

*Fylls i av #11.* Material 3 Expressive och Navigation 3 är nya; här står de artefakter,
versioner och API-namn som faktiskt kompilerats i projektet. Skills länkar hit. Använd
inget API som inte står här – lägg till det i samma PR som det börjar användas.

| Behov | API (paket.Klass / funktion) | Artefakt:version | Stabil/experimentell | Verifierad |
|---|---|---|---|---|
| Expressive-tema | | | | |
| Expressive-rörelse | | | | |
| Vågig linjär progress | | | | |
| Split-knapp | | | | |
| Knappgrupp | | | | |
| Flytande verktygsrad | | | | |
| Laddningsindikator | | | | |
| Flexibel toppbar | | | | |
| Formmorf för kryss | | | | |
| Navigation-nyckel | | | | |
| Back stack | | | | |
| Navigationsvisning | | | | |
| ViewModel per nav-post | | | | |

---

## Byggkonfiguration

*Fylls i av #11:* minSdk, targetSdk och compileSdk; versioner av AGP, Kotlin och Compose
BOM; Java 17; var den gemensamma byggkonfigurationen bor; hur `default_web_client_id` fås
utan `google-services.json`; `cpdCheck`-tröskel; uppmätt byggtid.
