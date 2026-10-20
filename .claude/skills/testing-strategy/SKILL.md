---
name: testing-strategy
description: ReseApotekets testregel (regel 2) — ingen beteendeändring utan tester på rätt nivå(er), och flaky tester är buggar. Ladda denna ALLTID när du lägger till, ändrar eller fixar funktionalitet, en ViewModel, en codec, PackingEngine, ett repository, en composable, en skärmram, en hook eller en bugg. Trigger-ord: test, tester, testa, JUnit, MockK, Turbine, Robolectric, Roborazzi, skärmdump, screenshot, Konsist, UiConsistencyTest, cpdCheck, kontraktstest, FakeCollection, emulator, rules-test, regression, flaky, instabil, täckning, TDD.
---

# Teststrategi i ReseApoteket

**Det generella står i skill `android:testing-strategy`** (pluginen): tester på varje berörd
nivå, befintliga tester uppdateras och tas aldrig bort, flaky är en bugg, mönstren,
skärmdumpsreglerna, fallgroparna och anti-mönstren. Här står det som är ReseApotekets:
nivåerna och filtren, hjälparna, emulatorn, skärmdumpsdetaljerna och kommandona.

Krav: NFR-serien i KRAVLISTA.md.

## Nivåer (enda källan för denna tabell)

| Nivå | Var | Verktyg | CI-filter |
|---|---|---|---|
| Domän + codecs | `core/src/test` | JUnit4 + kotlin.test | `core` |
| Arkitektur/konsistens + dokumentdrift | `app/src/test` | Konsist (`UiConsistencyTest`) | `app` |
| Dubbelkod | `core/` + `app/src` | `cpdCheck` | `core`, `app` |
| Navigation | `app/src/test` | JUnit4 (back stack som lista) | `app` |
| ViewModel/Repository | `app/src/test` | JUnit4, MockK, Turbine, `FakeCollection` | `app` |
| Compose-UI | `app/src/test` | Robolectric + `ui-test-junit4` | `app` |
| Skärmdump | `app/src/test` + `app/src/test/screenshots/` | Roborazzi | `app` |
| Security rules + rundtur | `tools/db/test` | `node --test`, `@firebase/rules-unit-testing`, Firebase-emulatorn | `tools` |
| Dokumentkontroller + hooktest | `.github/scripts/`, `.claude/hooks/test/` | `node --test` | alltid |
| Instrument (inkl. `CollectionContract` mot riktig `FirestoreCollection`) | `app/src/androidTest` | Android-emulator + Firebase-emulatorn | i PR som rör koden de testar (filter `instrumented`, skill `ci-budget`) |

Vilka sökvägar som sätter vilket filter, och vad som körs på `master`, står i skill
`ci-budget` (enda källan). **Selektiv körning ändrar inte kravet på att skriva
tester** – ett test som inte körs i en viss PR ska ändå finnas och vara grönt när dess
kod ändras.

## Vad ska testas när du ändrar X

| Ändring | Lägg till / uppdatera |
|---|---|
| `PackingEngine`, formatterare, validering | `:core`-test med kantfallen från skill `packing-engine` |
| Modell/codec | `assertCodecRoundTrip` + tolerans-hjälparna (skill `data-safety-backup`) |
| ViewModel | Initialt state, varje event → nytt state, fel-state; Turbine; `MainDispatcherRule` |
| Repository | Mot `FakeCollection<T>`; ändras `FirestoreCollection` → utöka `CollectionContract` – instrumenttesterna körs i PR:en |
| Skärm som använder en ram | `runListScreenContract` / `runEditScreenContract` / `runDetailScreenContract` + bara det unika |
| Delad komponent/ram | Robolectric-test, skärmdump ljust + mörkt, plats i `ComponentGallery` |
| `firestore.rules` | Rules-test: medlem, icke-medlem, oautentiserad |
| `tools/db` | Rundturstest och verktygets eget test |
| Hook eller dokumentkontroll | Nodetest med ett godkänt och ett underkänt exempel |
| Bugfix | **Först** ett test som reproducerar buggen och faller, sedan fixen |

## DRY i tester (regel 4 gäller även här)

Ramar och byggstenar testas **en gång, grundligt**. Feature-tester återanvänder
hjälparna och testar bara det som är unikt för funktionen:

| Hjälpare | Var | Ersätter |
|---|---|---|
| `FakeCollection<T>`, `FakeCollectionFactory`, `FakeStore` | `app/src/test/.../data/` | egna in-memory-fakes per repository |
| `CollectionContract`, `TestHouseholdScope`, `FixedClock` | `app/src/sharedTest/.../data/` (JVM mot fake, enhet mot Firebase-emulatorn) | separata tester per repository-implementation |
| `assertCodecContract` (samlar `assertEveryFieldDiffersFromDefault`, `assertCodecRoundTrip`, `assertToleratesMissingFields`, `assertIgnoresUnknownFields`, `assertEncodesAllFields`), `assertVariantCodecContract` | `core/src/test/.../schema/CodecAssertions.kt` | egna codec-asserts |
| `MainDispatcherRule` | `app/src/test/.../testing/` | egen `Dispatchers.setMain` per test |
| `HouseholdFixture` (riktig `HouseholdSession` + fejkad Firestore + `FakeHouseholdDirectory`, `EnsureHouseholdUseCase`) | `app/src/test/.../testing/` | egen uppställning av hushåll och session per test |
| `FakeAuthRepository` | `app/src/test/.../ui/auth/` | Google-inloggning i ViewModel-tester |
| `captureLightAndDark(name) { … }`, `rule.captureLightAndDark(name, settle) { … }` och `rule.captureScreenLightAndDark(name, open) { … }` | `app/src/test/.../testing/Screenshots.kt` | egna Roborazzi-anrop; regelvarianten behåller en visad snackbar mellan bilderna, skärmvarianten fotograferar dialoger, menyer och sheets |
| `runListScreenContract(contracts, item, itemText, emptyTitle, addLabel) { state, onAdd, onRetry -> … }` | android-ui `ui-testing` (`se.partee71.ui.testing`); `contracts` i `app/src/test/.../ui/Contracts.kt` | att testa tomt/fel/laddar/lägg till per skärm |
| `runEditScreenContract(contracts, editor, makeInvalid, makeValid, invalidMessage) { state, events, onSave, onClose -> … }` | android-ui `ui-testing` | att testa spara-aktivering/släng-dialog (pilen och systemets bakåt)/sparfel per skärm |
| `runDetailScreenContract(contracts, value, valueText) { state, onRetry, onEdit, onBack -> … }` | android-ui `ui-testing` | att testa laddar/läsfel/finns inte/Redigera/tillbaka per skärm |
| `test/fixtures/household.json` | `tools/db/test/` | egna seeds per Nodetest |

Behövs samma testuppställning på två ställen → gör en hjälpare innan det tredje.

Tester som delas mellan JVM och enhet ligger i `app/src/sharedTest` (t.ex. `CollectionContract`).
Där och i `app/src/androidTest` heter testmetoderna vanliga identifierare utan mellanslag
(`upsert_syns_i_observe`) – D8 kan inte dexa metodnamn med mellanslag. JVM-tester får ha
svenska meningar i backticks.
`cpdCheck` täcker även testkällor.

## Projektmönster

- Repositories byggs i test med `FakeCollection`.
- **`:core` är synkron** – testa rena funktioner direkt, inga coroutines.

## Tester mot Firebase-emulatorn

Tester i `tools/db/test/` och instrumenttester mot Firestore körs **endast mot emulatorn**,
via den gemensamma testhjälparen som avbryter om `FIRESTORE_EMULATOR_HOST` saknas och
använder projekt-ID:t `demo-reseapoteket`. En session med `FIREBASE_SERVICE_ACCOUNT` i
miljön får aldrig köra dem utan emulator (skill `data-safety-backup`).

## Dokumentdrift-kontrollen

`UiConsistencyTest` jämför komponent- och ramtabellen i skill `shared-ui-components` med
koden i båda riktningar, och att varje publik komponent har skärmdumpar och en plats i
`ComponentGallery`.

## Skärmdumpar (Roborazzi)

- Varje delad komponent och varje ramtillstånd: ljust + mörkt. `ComponentGallery` får en
  extra bild med `fontScale = 1.3`.
- Filnamn `app/src/test/screenshots/<Komponent>_<variant>_<light|dark>.png`.
- Deterministik: fasta mått (`@Config(qualifiers = "w390dp-h844dp-xxhdpi")`), animationer
  av, ingen klocka, inga slumpvärden.
- **Avsiktlig designändring** → `./gradlew :app:recordRoborazziDebug` och nya bilder i
  **samma** PR, med före/efter i PR-beskrivningen.
- **Oväntad diff** är en bugg eller regression – aldrig något som "bara spelas in på nytt".

## Innan du anser dig klar

```bash
./gradlew :core:test                                  # om core/ ändrats
./gradlew :app:testDebugUnitTest :app:verifyRoborazziDebug cpdCheck   # om app/ eller core/ ändrats
./gradlew spotlessApply                                              # kodstil före push (inte i CI)
./gradlew :app:lintDebug :app:assembleRelease         # om manifest, resurser, proguard eller byggfiler ändrats
firebase emulators:exec --only firestore "node --test 'tools/db/test/*.test.mjs'"  # om tools/db/ eller rules ändrats – bara mot emulatorn
node --test '.github/scripts/*.test.mjs'              # dokumentkontroller, alltid
node --test '.claude/hooks/test/*.test.mjs'           # hooktest, alltid
```

**Kör kontrollerna lokalt innan varje push** – en röd CI-runda är det dyraste som finns (skill
`ci-budget`). Saknar molnsessionen Android SDK: kör `.claude/hooks/install-android-sdk.sh` först.
Maven Central nås via Googles spegel (session-start-hooken lägger
`maven-central-mirror.init.gradle.kts` i `~/.gradle/init.d/`), så att Gradle slipper 429.
`LC_ALL=C.UTF-8` sätts av `.claude/settings.json`. Kör en Gradle-körning i taget. Bara
instrumenttesterna kräver Android-emulator och körs i Actions (`instrumented.yml`, i PR:er som
rör koden de testar). Går ett kommando inte att köra (nät, SDK saknas): säg det i PR:en. Vid
rött CI – låt agenten `android:ci-doktor` diagnostisera.

## För testskrivaren

Agenten `android:testskrivare` (pluginen) läser det här utöver tabellerna ovan:

- **Läs också** `data-safety-backup` vid data och `packing-engine` vid beräkning.
- **Hjälparna:** `FakeCollection<T>`, `CollectionContract`, `assertCodecContract` och de andra
  codec-hjälparna, `MainDispatcherRule`, `runListScreenContract`, `runEditScreenContract`,
  `runDetailScreenContract` (android-ui `ui-testing`, med `contracts`) och fixturen
  `tools/db/test/fixtures/household.json`.
- **Firestore** bara mot emulatorn (`demo-reseapoteket`).
