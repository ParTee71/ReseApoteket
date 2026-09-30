---
name: ci-budget
description: ReseApotekets CI-regel (regel 5) — en PR får kosta högst ca 8 Actions-minuter, och tester körs bara när koden de testar har ändrats. Enda källan för tabellen över vad som körs när. Ladda denna ALLTID när du rör .github/workflows, composite actions, Gradle-inställningar, nya beroenden eller plugins, nya moduler, testnivåer eller när CI är långsam. Trigger-ord: CI, GitHub Actions, workflow, android.yml, pipeline, byggtid, Actions-minuter, cache, paths-filter, selektiv, emulator, lint, release-bygge, nytt beroende, ny modul, långsam, budget.
---

# CI-budget

**Regel:** PR-flödet ska ta högst ca **8 minuter** på varm cache (`timeout-minutes: 15`
som skyddsnät). Dyra steg körs inte per PR. Tester körs bara när koden de testar har
ändrats. `master` kör alltid allt.

## Vad som körs när (enda källan för denna tabell)

Jobbet `changes` i `.github/workflows/android.yml` startar alltid (sekunder) och avgör
resten med `dorny/paths-filter`.

<!-- ci-table:start -->
| Filter | Sökvägar | Körs |
|---|---|---|
| `core` | `core/**` | `:core:test` + allt i raden `app` (appen beror på `:core`) |
| `app` | `app/**`, `.claude/skills/shared-ui-components/**` | `:app:testDebugUnitTest` (inkl. `UiConsistencyTest`) + `verifyRoborazziDebug` + `cpdCheck` + `:app:assembleDebugAndroidTest` (kompilerar och dexar instrumenttesterna utan att köra dem) |
| `tools` | `firestore.rules`, `firestore.indexes.json`, `firebase.json`, `tools/db/**` | rules- och rundturstester (Node) mot Firebase-emulatorn |
| `build` | `*.gradle.kts`, `gradle/**`, `gradle.properties`, `build-logic/**`, `.github/**` | allt ovan |
| – | allt annat (`*.md`, `.claude/**`) | bara `changes` med dokumentkontroller |
<!-- ci-table:end -->

- **Dokumentkontrollerna körs alltid** i `changes` (denna tabell mot filtren, länkar och
  skill-/agentnamn, hooktestet), som två kommandon – samma form som de förhandsgodkända:
  `node --test '.github/scripts/*.test.mjs'` och `node --test '.claude/hooks/test/*.test.mjs'`.
- **`push` till `master`** sätter alla filter till sant – baslinje och cache-uppvärmning.
- **Gradle build cache** (`org.gradle.caching=true` + `gradle/actions/setup-gradle`) gör
  att oförändrade testtasks blir `FROM-CACHE`/`UP-TO-DATE` även inom en modul. Det kräver
  deterministiska tester (regel 2).
- **Firebase-emulatorn är inte Android-emulatorn.** Den kräver Java 21 och `firebase-tools`,
  tar ca 1–2 minuter med cache och körs bara i jobbet `rules` (filter `tools`). Android-emulatorn körs aldrig per PR.
- **Node ≥ 22** pinnas i alla jobb som kör `node --test` (globmönster kräver Node 21+).
- **Hoppade jobb rapporterar ingen status.** Därför är jobbet `ci-ok`
  (`needs: [changes, gradle, rules]`, `if: always()`) det enda obligatoriska statuskravet.

- **`gradle`** kör en enda Gradle-körning: `cpdCheck` plus `:core:test` och/eller app-uppgifterna
  enligt filtren, med `--configuration-cache --continue`. Testrapporter laddas alltid upp;
  Roborazzi-diffar och CPD-rapporten vid fel, länkade i jobbsammanfattningen.
- **`rules`** hoppar över (med en notis) så länge `tools/db/package.json` eller `firebase.json`
  saknas (#15, #17).
- **`release.yml`** anropar `android.yml`, `instrumented.yml` och `quality.yml`
  (`workflow_call`) som grindar – samma jobb, ingen kopia.

### Dokumentdrift
`.github/scripts/check-ci-table.mjs` läser tabellen mellan `ci-table`-markeringarna och
filtren i `android.yml` och fäller `changes` om de skiljer sig. Ändra alltid båda i samma
PR. Ny modul = ny rad här **och** nytt filter. `.github/scripts/check-links.mjs` kontrollerar
relativa länkar och nämnda skills/agenter i `CLAUDE.md`, `README.md`, `ARKITEKTUR.md`,
`KRAVLISTA.md` och `.claude/**`, och att tabellerna i `CLAUDE.md` listar exakt de skills och
agenter som finns. Framåtreferenser står i `.github/scripts/link-allowlist.txt`.

## Inte i PR-flödet (utan uttryckligt beslut)

| Steg | Var det körs i stället |
|---|---|
| Instrumenttester på Android-emulator (inkl. `CollectionContract` mot riktig `FirestoreCollection`) | `instrumented.yml`: `workflow_dispatch` och release; körs på begäran före merge när `data/firestore/` ändrats |
| `lintDebug` | `quality.yml`: veckovis och release |
| `assembleRelease` (minifierat) | `quality.yml`: veckovis och release |
| Matrisbyggen, flera API-nivåer | inte alls i dag |
| Backup | `backup.yml`: veckovis cron |

## Tekniker som håller tiden nere

- `gradle/actions/setup-gradle`: cache skrivs från `master`, läses i PR.
- `org.gradle.configuration-cache=true`, `org.gradle.parallel=true`, `--configuration-cache`.
- `concurrency: { group: ci-${{ github.workflow }}-${{ github.ref }}, cancel-in-progress: <bara för PR> }`
  – en ny push avbryter PR:ens förra körning, men `master`-baslinjen och release-grindarna avbryts aldrig.
- Robolectric-SDK-jar och Firebase-emulatorn cachas.
- Gemensamma steg i composite actions (`.github/actions/setup-android`, `doc-checks`,
  `open-issue-on-failure`) – en ändring, alla flöden.
- `:core` utan Android-beroenden kompilerar och testar på sekunder.

## När du lägger till något

- **Nytt beroende eller plugin:** ange uppmätt byggtidseffekt i PR-beskrivningen.
- **Ny testnivå eller nytt verktyg:** lägg det i rätt filter; aldrig ovillkorligt i `gradle`.
- **Över budget:** eget issue med mätning och förslag – inte en tyst höjning av timeouten.
- **Lokalt:** kör bara modulen du ändrat, som CI gör. Aldrig `./gradlew test` av vana.

## Anti-mönster

- `paths-ignore` på workflow-nivå (dokumentändringar skulle då aldrig kontrolleras).
- Göra `gradle` eller `rules` till obligatoriska statuskrav (hoppade jobb blockerar PR:en).
- Köra emulator "bara den här gången" i PR-flödet.
- Kopiera setup-steg mellan workflows i stället för en composite action.
- Höja timeouten i stället för att hitta orsaken till att det blev långsamt.
