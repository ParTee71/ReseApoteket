---
name: ci-budget
description: Regel 5 i ReseApoteket — en PR kostar högst ca 8 Actions-minuter och tester körs bara när koden de testar ändrats. Enda källan för tabellen över vad som körs när. Ladda alltid vid .github/workflows, Gradle-inställningar, nya beroenden, plugins, moduler eller testnivåer, och när CI är långsam. Trigger-ord: CI, GitHub Actions, workflow, android.yml, byggtid, Actions-minuter, cache, paths-filter, emulator, lint, nytt beroende.
---

# CI-budget

**Regel:** PR-flödet ska ta högst ca **8 minuter** på varm cache (`timeout-minutes: 15`
som skyddsnät) – plus instrumenttesterna (ca 9 min) när en PR rör koden de testar
(användarens beslut 2026-10-03). Övriga dyra steg körs inte per PR. Tester körs bara när koden
de testar har ändrats. `master` kör allt utom instrumenttesterna (PR:en har kört dem).

## Vad som körs när (enda källan för denna tabell)

Jobbet `changes` i `.github/workflows/android.yml` startar alltid (sekunder) och avgör
resten med `dorny/paths-filter`.

<!-- ci-table:start -->
| Filter | Sökvägar | Körs |
|---|---|---|
| `core` | `core/**`, `tools/db/test/fixtures/**` | `:core:test` + allt i raden `app` (appen beror på `:core`; exportformatets tester läser tools/db:s testdata) |
| `app` | `app/**`, `.claude/skills/shared-ui-components/**` | `:app:testDebugUnitTest` (inkl. `UiConsistencyTest`) + `verifyRoborazziDebug` + `cpdCheck` + `:app:assembleDebugAndroidTest` (kompilerar och dexar instrumenttesterna utan att köra dem – utom när filtret `instrumented` kör dem) |
| `tools` | `firestore.rules`, `firestore.indexes.json`, `firebase.json`, `tools/db/**`, `app/src/main/kotlin/se/partee71/reseapoteket/data/firestore/Paths.kt` | rules- och rundturstester (Node) mot Firebase-emulatorn, och samlingslistan mot `Paths.kt` |
| `build` | `*.gradle.kts`, `gradle/**`, `gradle.properties`, `build-logic/**`, `.github/**` | allt ovan utom instrumenttesterna (de har egna sökvägar) |
| `instrumented` | `app/src/**`, `core/src/**`, `*.gradle.kts`, `app/build.gradle.kts`, `core/build.gradle.kts`, `gradle/**`, `gradle.properties`, `build-logic/**`, `firestore.rules`, `firebase.json`, `tools/db/package-lock.json`, `.github/workflows/instrumented.yml`, `.github/actions/setup-android/**` | instrumenttesterna på Android-emulator (`instrumented.yml`), bara i PR, och måste då vara gröna för merge (`ci-ok`); då hoppar `gradle` över `assembleDebugAndroidTest`. `release.yml` kör dem inte igen om dessa sökvägar är oförändrade sedan en PR där de var gröna |
| – | allt annat (`*.md`, `.claude/**`) | bara `changes` med dokumentkontroller |
<!-- ci-table:end -->

- **Dokumentkontrollerna körs alltid** i `changes` (denna tabell mot filtren, länkar och
  skill-/agentnamn, hooktestet), som två kommandon – samma form som de förhandsgodkända:
  `node --test '.github/scripts/*.test.mjs'` och `node --test '.claude/hooks/test/*.test.mjs'`.
  I samma steg körs testerna för läkemedelslistans byggskript,
  `node --test 'tools/medicines/test/*.test.mjs'` – Node utan beroenden, under en sekund, så de
  får inget eget filter. Själva skriptet (`tools/medicines/build.mjs`) körs aldrig i CI, bara vid
  release (skill `android-projekt` → Release).
- **`push` till `master`** sätter alla filter till sant – baslinje och cache-uppvärmning.
- **Gradle build cache** (`org.gradle.caching=true` + `gradle/actions/setup-gradle`) gör
  att oförändrade testtasks blir `FROM-CACHE`/`UP-TO-DATE` även inom en modul. Det kräver
  deterministiska tester (regel 2).
- **Firebase-emulatorn är inte Android-emulatorn.** Den kräver Java 21 och `firebase-tools`,
  tar ca 1–2 minuter med cache och körs bara i jobbet `rules` (filter `tools`). Android-emulatorn
  körs bara i jobbet `instrumented` (filter `instrumented`).
- **Node ≥ 22** pinnas i alla jobb som kör `node --test` (globmönster kräver Node 21+).
- **Hoppade jobb rapporterar ingen status.** Därför är jobbet `ci-ok`
  (`needs: [changes, gradle, rules, instrumented]`, `if: always()`) det enda obligatoriska
  statuskravet. Det godtar `success` och `skipped` och fäller allt annat: **körs instrumenttesterna
  måste de vara gröna för merge** – ett rött eller avbrutet `instrumented` fäller `ci-ok`, ett
  överhoppat gör det inte (användarens beslut 2026-10-18).

- **`gradle`** kör en enda Gradle-körning: `cpdCheck` plus `:core:test` och/eller app-uppgifterna
  enligt filtren, med `--configuration-cache --continue`. Testrapporter laddas alltid upp;
  Roborazzi-diffar och CPD-rapporten vid fel, länkade i jobbsammanfattningen.
- **`rules`** hoppar över (med en notis) om `tools/db/package.json` eller `firebase.json`
  saknas.
- **`release.yml`** kör inget två gånger: en snabb `gate` kontrollerar version, secrets och CI-resultatet på
  commiten (master-pushen) i stället för att köra om det – och stoppar allt innan något dyrt startar;
  lint och det signerade minifierade bygget går i en Gradle-körning
  (`build`), parallellt med `instrumented.yml` (`workflow_call`) – som hoppas över när koden
  redan testats grönt i en PR (`.github/scripts/instrumented-paths.mjs`; har en annan PR ändrat
  samma sökvägar sedan dess körs de igen); rules deployas bara om de
  ändrats sedan förra releasen. Ett fallerat jobb körs om ensamt, aldrig hela releasen.
- **`quality.yml`** (veckovis) hoppar över om master inte ändrats sedan senaste gröna
  kvalitets- eller releasekörning.
- **Versionen** står i `version.properties` (utanför filtren) och höjs i funktions-PR:en, så
  höjningen kostar inga extra jobb och releasen behöver ingen egen PR.
- Varje jobb debiteras uppåt till hel minut – slå ihop korta steg i ett jobb hellre än att
  lägga till jobb.

### Dokumentdrift
`.github/scripts/check-ci-table.mjs` läser tabellen mellan `ci-table`-markeringarna och
filtren i `android.yml` och fäller `changes` om de skiljer sig. Ändra alltid båda i samma
PR. Ny modul = ny rad här **och** nytt filter. `.github/scripts/check-links.mjs` kontrollerar
relativa länkar och nämnda skills/agenter i `CLAUDE.md`, `README.md`, `ARKITEKTUR.md`,
`KRAVLISTA.md` och `.claude/**`, och att tabellerna i `.claude/README.md` listar exakt de skills och
agenter som finns. Framåtreferenser står i `.github/scripts/link-allowlist.txt`.

## Inte i PR-flödet (utan uttryckligt beslut)

| Steg | Var det körs i stället |
|---|---|
| `lintDebug` | `quality.yml`: veckovis när master ändrats; vid release `lintRelease` i `release.yml` |
| `assembleRelease` (minifierat) | `quality.yml`: veckovis när master ändrats; vid release det signerade bygget i `release.yml` |
| Matrisbyggen, flera API-nivåer | inte alls i dag |
| Backup | `backup.yml`: veckovis cron |
| Deploy av `firestore.rules` | `rules.yml`: `workflow_dispatch` (dry-run som standard, deploy bara från `master`); `release.yml` före publiceringen, bara om de ändrats sedan förra releasen |

## Tekniker som håller tiden nere

- `gradle/actions/setup-gradle`: cache skrivs från `master`, läses i PR.
- `org.gradle.configuration-cache=true`, `org.gradle.parallel=true`, `--configuration-cache`.
- `concurrency: { group: ci-${{ github.workflow }}-${{ github.ref }}, cancel-in-progress: <bara för PR> }`
  – en ny push avbryter PR:ens förra körning, men `master`-baslinjen och release-grindarna avbryts aldrig.
- Robolectric-SDK-jar och Firebase-emulatorn cachas.
- Gemensamma steg i composite actions (`.github/actions/setup-android`, `doc-checks`,
  `open-issue-on-failure`, `setup-tools-db`, `rules-tests`, `deploy-rules`) – en ändring, alla flöden.
- `setup-android` provar först att `GPR_TOKEN` når android-ui och stoppar på en sekund om secreten
  saknas eller är fel, i stället för efter bygget och emulatorn.
- `:core` utan Android-beroenden kompilerar och testar på sekunder.

## När du lägger till något

- **Nytt beroende eller plugin:** ange uppmätt byggtidseffekt i PR-beskrivningen.
- **Ny testnivå eller nytt verktyg:** lägg det i rätt filter; aldrig ovillkorligt i `gradle`.
- **Över budget:** eget issue med mätning och förslag – inte en tyst höjning av timeouten.
- **Lokalt före push** (skill `testing-strategy`): de kontroller ändringen berör, aldrig
  `./gradlew test` av vana. Lokala körningar kostar inga Actions-minuter – en röd CI-runda gör det.

## Anti-mönster

- `paths-ignore` på workflow-nivå (dokumentändringar skulle då aldrig kontrolleras).
- Göra `gradle` eller `rules` till obligatoriska statuskrav (hoppade jobb blockerar PR:en).
- Köra emulatorn i en PR som inte rör filtret `instrumented`, eller "bara den här gången".
- Kopiera setup-steg mellan workflows i stället för en composite action.
- Höja timeouten i stället för att hitta orsaken till att det blev långsamt.
