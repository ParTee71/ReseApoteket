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
| `core` | `core/**` | `:core:test` + `:app:testDebugUnitTest` + `verifyRoborazziDebug` + `cpdCheck` |
| `app` | `app/**`, `.claude/skills/shared-ui-components/**` | `:app:testDebugUnitTest` (inkl. `UiConsistencyTest`) + `verifyRoborazziDebug` + `cpdCheck` + `:app:compileDebugAndroidTestKotlin` |
| `tools` | `firestore.rules`, `firestore.indexes.json`, `tools/db/**`, `app/src/main/**/data/firestore/**` | rules-, rundturs- och kontraktstester mot Firestore-emulatorn |
| `build` | `*.gradle.kts`, `gradle/**`, `gradle.properties`, `build-logic/**`, `.github/**` | allt ovan |
| – | allt annat (`*.md`, `.claude/**`) | bara `changes` med dokumentkontroller |
<!-- ci-table:end -->

- **Dokumentkontrollerna körs alltid** i `changes`: `node --test .github/scripts/ .claude/hooks/test/`
  (denna tabell mot filtren, länkar och skill-/agentnamn, hooktestet).
- **`push` till `master`** sätter alla filter till sant – baslinje och cache-uppvärmning.
- **Gradle build cache** (`org.gradle.caching=true` + `gradle/actions/setup-gradle`) gör
  att oförändrade testtasks blir `FROM-CACHE`/`UP-TO-DATE` även inom en modul. Det kräver
  deterministiska tester (regel 2).
- **Hoppade jobb rapporterar ingen status.** Därför är jobbet `ci-ok`
  (`needs: [changes, gradle, rules]`, `if: always()`) det enda obligatoriska statuskravet.

### Dokumentdrift
`.github/scripts/check-ci-table.mjs` läser tabellen mellan `ci-table`-markeringarna och
filtren i `android.yml` och fäller `changes` om de skiljer sig. Ändra alltid båda i samma
PR. Ny modul = ny rad här **och** nytt filter.

## Inte i PR-flödet (utan uttryckligt beslut)

| Steg | Var det körs i stället |
|---|---|
| Instrumenttester på emulator | `instrumented.yml`: `workflow_dispatch` och release |
| `lintDebug` | `quality.yml`: veckovis och release |
| `assembleRelease` (minifierat) | `quality.yml`: veckovis och release |
| Matrisbyggen, flera API-nivåer | inte alls i dag |
| Backup | `backup.yml`: veckovis cron |

## Tekniker som håller tiden nere

- `gradle/actions/setup-gradle`: cache skrivs från `master`, läses i PR.
- `org.gradle.configuration-cache=true`, `org.gradle.parallel=true`, `--configuration-cache`.
- `concurrency: { group: ci-${{ github.ref }}, cancel-in-progress: true }`.
- Robolectric-SDK-jar och Firebase-emulatorn cachas.
- Gemensamma steg i composite actions (`.github/actions/setup-android`,
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
