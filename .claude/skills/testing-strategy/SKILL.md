---
name: testing-strategy
description: ReseApotekets testregel (regel 2) — ingen beteendeändring utan tester på rätt nivå(er), och flaky tester är buggar. Ladda denna ALLTID när du lägger till, ändrar eller fixar funktionalitet, en ViewModel, en codec, PackingEngine, ett repository, en composable, en skärmram, en hook eller en bugg. Trigger-ord: test, tester, testa, JUnit, MockK, Turbine, Robolectric, Roborazzi, skärmdump, screenshot, Konsist, UiConsistencyTest, cpdCheck, kontraktstest, FakeCollection, emulator, rules-test, regression, flaky, instabil, täckning, TDD.
---

# Teststrategi: tester på alla nivåer

**Regel:** en ändring av beteende är inte klar förrän tester lagts till eller uppdaterats
på varje berörd nivå. Befintliga tester **uppdateras** så att de speglar det nya
beteendet – de tas aldrig bort, `@Ignore`:as eller försvagas för att bli gröna.
**Ett test som ibland faller är en bugg** – hitta grundorsaken (klocka, ordning, delat
tillstånd, otestad asynkronitet) och gör det deterministiskt. Kör aldrig bara om.

Krav: NFR-serien i KRAVLISTA.md.

## Nivåer (enda källan för denna tabell)

| Nivå | Var | Verktyg | Körs i PR när |
|---|---|---|---|
| Domän + codecs | `core/src/test` | JUnit4 + kotlin.test | `core/` ändrats |
| Arkitektur/konsistens + dokumentdrift | `app/src/test` | Konsist (`UiConsistencyTest`) | `app/`, `core/` eller `shared-ui-components`-skillen ändrats |
| Dubbelkod | `core/` + `app/src` | `cpdCheck` | `app/` eller `core/` ändrats |
| Navigation | `app/src/test` | JUnit4 (back stack som lista) | `app/` eller `core/` ändrats |
| ViewModel/Repository | `app/src/test` | JUnit4, MockK, Turbine, Fake-repositories | `app/` eller `core/` ändrats |
| Compose-UI | `app/src/test` | Robolectric + `ui-test-junit4` | `app/` eller `core/` ändrats |
| Skärmdump | `app/src/test` + `app/src/test/screenshots/` | Roborazzi | `app/` eller `core/` ändrats |
| Security rules + rundtur + kontrakt | `tools/db/test` | `node --test`, `@firebase/rules-unit-testing`, emulator | `firestore.rules`, `tools/db/` eller `data/firestore/` ändrats |
| Dokumentkontroller + hooktest | `.github/scripts/`, `.claude/hooks/test/` | `node --test` | alltid (sekunder) |
| Instrument | `app/src/androidTest` | Emulator | på begäran och i release-flödet |

Byggfiler eller CI-flöden ändrade → allt utom instrument. `master` kör alltid allt.
Mekanismen står i skill `ci-budget`. **Selektiv körning ändrar inte kravet på att skriva
tester** – ett test som inte körs i en viss PR ska ändå finnas och vara grönt när dess
kod ändras.

## Vad ska testas när du ändrar X

| Ändring | Lägg till / uppdatera |
|---|---|
| `PackingEngine`, formatterare, validering | `:core`-test med kantfallen från skill `packing-engine` |
| Modell/codec | `assertCodecRoundTrip` + tolerans-hjälparna (skill `data-safety-backup`) |
| ViewModel | Initialt state, varje event → nytt state, fel-state; Turbine; `MainDispatcherRule` |
| Repository | Mot `FakeCollection<T>`; `CollectionContract` om `FirestoreCollection` ändras |
| Skärm som använder en ram | `runListScreenContract` / `runEditScreenContract` + bara det unika |
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
| `FakeCollection<T>` | `app/src/test/.../data/` | egna in-memory-fakes per repository |
| `CollectionContract` | `app/src/test/.../data/` | separata tester per repository-implementation |
| `assertCodecRoundTrip`, `assertToleratesMissingFields`, `assertIgnoresUnknownFields` | `core/src/test/.../schema/` | egna codec-asserts |
| `MainDispatcherRule` | `app/src/test/.../testing/` | egen `Dispatchers.setMain` per test |
| `runListScreenContract(screen, data)` | `app/src/test/.../ui/` | att testa tomt/fel/laddar/lägg till per skärm |
| `runEditScreenContract(screen, valid, invalid)` | `app/src/test/.../ui/` | att testa spara-aktivering/släng-dialog/sparfel per skärm |
| `test/fixtures/household.json` | `tools/db/test/` | egna seeds per Nodetest |

Behövs samma testuppställning på två ställen → gör en hjälpare innan det tredje.
`cpdCheck` täcker även testkällor.

## Projektmönster

- **Fake framför mock för datalagret.** Repositories byggs i test med `FakeCollection`.
  MockK för samverkande beroenden som inte är datalager.
- **Injicera dispatcher**; kalla aldrig `Dispatchers.IO` direkt i testbar kod.
- **Turbine** för `StateFlow`/`Flow`, inte manuell `collect`.
- **`:core` är synkron** – testa rena funktioner direkt, inga coroutines.
- **Nytt konstruktorberoende på en ViewModel** → alla dess tester får beroendet.

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
node --test 'tools/db/test/*.test.mjs'                           # om tools/db/ eller rules ändrats (kräver emulator)
node --test '.github/scripts/*.test.mjs' '.claude/hooks/test/*.test.mjs'      # dokumentkontroller, alltid
```

Från telefonen går Gradle normalt inte att köra: skriv testerna, pusha och låt Actions
köra dem. Vid rött – låt agenten `ci-doktor` diagnostisera.

## Anti-mönster

- Ta bort, `@Ignore`:a eller försvaga ett rött test för att bli klar.
- Köra om ett test tills det blir grönt.
- Testa implementation (interna anrop) i stället för beteende.
- Skriva om ramens beteende-tester i varje feature i stället för att köra kontraktet.
- Spela in nya referensbilder för att tysta en diff du inte förstår.
- Ny funktion utan test "för att den är liten".
