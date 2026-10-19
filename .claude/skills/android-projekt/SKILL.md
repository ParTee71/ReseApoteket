---
name: android-projekt
description: ReseApotekets anpassningar av de generella android:-skillsen från pluginen android@claude-android (android-dev, kotlin-coroutines, kotlin-flows, compose-expert, navigation-3, accessibility-compose, android-gradle-logic, firebase-auth, android-intent-security, firebase-security-rules-auditor, mockup, implement-issue, refine-issue, release). Ladda den alltid tillsammans med en android:-skill – den säger vad som gäller just här och går före pluginens generella råd. Täcker projektets klasser och mönster, nuvarande avvikelser från pluginen, Gradle-uppsättningen, inloggning med hushåll, a11y-detaljer för appens komponenter, mockup-processen med mallens länk, arbetsflödet från issue till PR (repo, lager, agenter, /code-review, commit-områden), issuets repodelar (etiketter, testplan, Agent · modell · effort) och releasens repodelar (version.properties, release.yml, rules, läkemedelslistan, schemaVersion). Trigger-ord: Android, Kotlin, ViewModel, Hilt, Compose, coroutine, dispatcher, flöde, StateFlow, händelse, effekt, navigering, flik, tillgänglighet, TalkBack, Gradle, beroende, inloggning, logga in, logga ut, hushåll, google-services, SHA-1, manifest, Intent, firestore.rules, mockup, skiss, ny skärm, ny komponent, Design-canvas, issue, genomför, implementera, kör #N, kör nästa, förfina, planera, PR, commit, granskning, agent, modell, effort, release, släpp, ny version, versionshöjning, version.properties, release.yml, läkemedelslistan, bygg APK.
---

# Android i ReseApoteket

De generella skillsen kommer från pluginen `android@claude-android` och heter `android:<namn>`.
Pluginen är generell; det som bara gäller ReseApoteket står här, en sektion per pluginskill.
**CLAUDE.md:s fem regler och repots egna skills går före båda.** Paketträd, versioner och
verifierade API:er står bara i [ARKITEKTUR.md](../../../ARKITEKTUR.md).

## Baslinje (android:android-dev)

- MVVM med `StateFlow<UiState>` och sealed events är beslutat i ADR-001 (ARKITEKTUR.md) – byt inte till MVI.
- Material 3 Expressive via `ui/theme` (skill `ui-style`). Firestore med offline-cache är enda databasen, ingen Room; all åtkomst via `FirestoreCollection<T>` + `DocCodec<T>` (skill `firestore-data-layer`).
- `:core` (ren Kotlin/JVM: modeller, codecs, `PackingEngine`) + `:app`. Utseende och gemensamt beteende bara i `ui/theme`, `ui/components`, `ui/common`; Firestore bara i `data/firestore`.
- Fel mappas en gång till `DataError` med `suspendRunCatching` (`data/common/`): `FirestoreCollection` för data, `AuthRepository` för inloggning. Repositories skickar `Result<T>` vidare; skärmar visar felet med `DataError.toMessage()`; `DataError.Cancelled` är tyst.
- Ramarna (regel 4): listskärmar `EntityListScreen` + `asListUiState()`, redigering `EntityEditScreen` + `EditorState<T>` – aldrig egna varianter.
- Navigation: en Activity (`MainActivity`), back stacken är vanligt state i `navigation/`, övergångar en gång i `navigation/Transitions`.
- Bakgrundsjobb: inga WorkManager-jobb – Firestore synkar själv och backupen körs i GitHub Actions (`tools/db`, veckovis krypterad). Ingen backupkod i appen. Behövs bakgrundsarbete: skriv en skill först.
- **Nuvarande läge, avviker från pluginen:** `SavedStateHandle` i stället för assisted injection, ingen Renovate eller Baseline Profile, och fyra compose-rules-regler avstängda i ktlint för befintliga komponent-API:er (`parameter-naming`, `lambda-param-event-trailing`, `multiple-emitters-check`, `modifier-naming`, listade i `ktlintRules` i `build.gradle.kts`). Tas i egna PR:er senare; ny kod följer pluginen där det går.
- Kodstil: ktlint 1.8 (stil `android_studio`) med compose-rules via Spotless, bara lokalt och inte i CI (regel 5). Kör `./gradlew spotlessApply` före push; `spotlessCheck` kontrollerar. Nya CompositionLocals läggs i `compose_allowed_composition_locals` i samma map.
- Koden byggs för Java 17; JDK 21 kör bygget lokalt och i CI, eftersom Robolectric kräver 21 för SDK 36. Tester JVM först (JUnit, Turbine, Robolectric, Roborazzi), fakes byggda på `FakeCollection<T>` (skill `testing-strategy`).

## Coroutines (android:kotlin-coroutines)

- Firestore är redan main-safe: `await()` och `snapshots()` blockerar inte – ingen `withContext` runt dem. `FirestoreCollection` är enda stället som anropar Firestore och hanterar fel och `CancellationException`.
- `suspendRunCatching` finns en gång i `data/common/`; skriv ingen egen variant.
- `:core` är ren och synkron – inga coroutines eller dispatchers där.
- ViewModel-tester använder den delade `MainDispatcherRule` (`StandardTestDispatcher` via `Dispatchers.setMain`).
- Dispatchers: `di/DispatchersModule` ger `@IoDispatcher` och `@DefaultDispatcher`; bara `di/` nämner `Dispatchers.`, vilket scopet `[dispatchers]` i `ui-forbidden.txt` kontrollerar i `UiConsistencyTest` och hooken. Enhetstester ger `UnconfinedTestDispatcher()` eller `StandardTestDispatcher(testScheduler)`.

## Flöden (android:kotlin-flows)

- `AuthRepository.authState: Flow<AuthUser?>` – `callbackFlow` på `FirebaseAuth.AuthStateListener`.
- `FirestoreCollection<T>.observe(): Flow<List<T>>` – `snapshots()` via `DocCodec<T>`, enda källan till dataflöden; repositories exponerar dem oförändrade eller kombinerade.
- `FirestoreCollection.syncing: Flow<Boolean>` (`hasPendingWrites`) driver appens enda "synkas…"-indikator.
- `Flow<List<T>>.asListUiState()` – gemensam omvandling till `ListUiState<T>` för alla listskärmar.
- `HouseholdSession.activeHouseholdId: StateFlow<String?>` – `flatMapLatest` på den när hushållet byts.
- `SettingsStore` – DataStore-flöden för tema och packlisteinställningar.
- ViewModels: `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)`.
- Händelser från en ViewModel är fält i tillståndet som skärmen kvitterar (pluginen, Googles råd): fel `ErrorSnackbar` + `…ErrorShown`; formulärets klar/sparfel `EditorUiState.done`/`failure`, som `EntityEditScreen` kvitterar via `EditorEvents` (`EditorState`); konfettin `PackingListViewModel.celebrate` + `PackingListEvent.Celebrated`; delningen `SettingsUiState.share` + `SettingsEvent.ShareShown`. Skärmen agerar i en `LaunchedEffect` nyckelad på värdet. Ingen `Channel` eller `receiveAsFlow` i `ui/` – scopet `[events]` i `ui-forbidden.txt` kontrollerar det.

## Compose (android:compose-expert)

- Ordning: skill `shared-ui-components` (regel 4) går före allt, sedan `ui-style` (färg, typografi, form, rörelse, texter). Skärmdumpar och kontraktstester: `testing-strategy`.
- Tema: android-ui:s `AppTheme` (`MaterialExpressiveTheme`, `MotionScheme.expressive()`) med appens färger, typskala och former; `AppTheme.typography`, `AppTheme.shapes`, `Spacing`.
- Expressive- och Nav3-API:er används bara med namn och signaturer som står i ARKITEKTUR.md → "Verifierade API:er" (verifierade i Gradle-skelettet, #11).
- Inga bildbibliotek: avatarer är emoji, ikoner Material Symbols.
- En mockup bryts ner i ramarna och katalogen (`shared-ui-components`); saknas ett element blir det en ny delad komponent.
- Skärmstruktur: `XScreen(vm = hiltViewModel())` samlar state och lämnar till ramen, t.ex. `EntityListScreen(title, state = state.list, row, onAdd, onHide, onUndo)` med `PersonsEvent.Add`/`Archive`/`UndoArchive`.
- Fel, laddning och knappar: `DataError.toMessage()` i snackbar, `AppLoading`, `AppButton(loading = …)`. Kort och sektioner: `ApotekCard` + `SectionHeader`.

## Navigation 3 (android:navigation-3)

Googles referens är generell. Här gäller vår `AppBackStack` (`navigation/AppBackStack.kt`) med en
stack per flik, nycklarna i `navigation/AppKeys.kt`, övergångarna i `navigation/Transitions` och
Hilt via `hiltViewModel`.

## Tillgänglighet (android:accessibility-compose)

Krav NFR-11 (TalkBack, stor text, kontrast ≥ 4,5:1, `ThemeContrastTest`).

- Meningsbärande ikoner: `AppIconButton`, statusikon, `PersonAvatar`. Dekorativt (`contentDescription = null`): sektionsikon bredvid rubrik, `Confetti`.
- Små ikoner får tryckyta via `AppIconButton`.
- Text i `sp` via `AppTypography`; `ComponentGallery` har en Roborazzi-skärmdump med `fontScale = 1.3`.
- `ItemRow` och `CheckRow` slås ihop med `mergeDescendants`. `CheckRow`: `Role.Checkbox` + `stateDescription` "packad"/"inte packad", t.ex. "Enalapril 10 mg, 18 tabletter, inte packad".
- `QuantityStepper`: "Minska"/"Öka" + värde och enhet. `SuitcaseProgress`: "9 av 14 packat". `UndoSnackbar`: ångra nåbar och annonserad.
- Sektionsfärger (Medicin/Kosttillskott/Övrigt) och personfärger kompletteras alltid med text eller ikon. Dämpad text på vit yta minst `#6B5A50`.
- Testexempel: `onNode(hasStateDescription("inte packad"))`.

## Gradle (android:android-gradle-logic)

Versioner, SDK-nivåer och skäl: ARKITEKTUR.md → "Byggkonfiguration".

| Modul | Plugins | Regel |
|---|---|---|
| `:core` | `kotlin("jvm")`, `kotlin("plugin.serialization")` | Inga Android- eller Firebase-beroenden. Beroenden `kotlinx-serialization-json`, `kotlinx-datetime`; test `junit`, `kotlin-test`. |
| `:app` | `android.application` (AGP 9, inbyggd Kotlin), `kotlin.compose`, `ksp`, `hilt`, `kotlin.serialization`, `google-services` (bara när `app/google-services.json` finns), `roborazzi` | `implementation(project(":core"))`. |

- Gemensam konfiguration (Java 17 utan toolchain via `sourceCompatibility`/`targetCompatibility` + `jvmTarget`, kompilatorflaggor, testtimeout, testloggning) en gång i rotens `build.gradle.kts` (`subprojects {}` + `plugins.withId {}`), vald i #11.
- **android-ui** (`se.partee71.ui:ui-theme`/`ui-components`, `androidUi` i katalogen) hämtas från GitHub Packages med repot i `settings.gradle.kts` (pluginens android-gradle-logic → "GitHub Packages (android-ui)"): sessionen läser med `GPR_USER`/`GPR_TOKEN`, varje Gradle-steg i `.github/workflows` med `env: GPR_TOKEN: ${{ secrets.GPR_TOKEN }}` (`instrumented.yml` får secreten av anroparen). Compose BOM, `material3`, Kotlin och AGP hålls i takt med biblioteket.
- `material3` är pinnad utanför Compose BOM (ARKITEKTUR.md → "Verifierade API:er"); skälet står i katalogen.
- `androidTest`-versioner som krockar höjs i `constraints {}` i `app/build.gradle.kts` med kommentar.
- `cpdCheck` (PMD CPD via CLI som `JavaExec`) över `core/src`, `app/src/main`, `app/src/test`, kopplad till `check`. `gradle.properties`: caching, configuration cache, parallel (skill `ci-budget`).
- `google-services.json` checkas aldrig in; CI skriver den från hemligheten `GOOGLE_SERVICES_JSON`. Utan filen ger `app/src/authStub/res` (bara debug) `default_web_client_id` och en Firebase-konfiguration för demoprojektet `demo-reseapoteket`, så att appen startar (t.ex. `AppLaunchTest`).
- Nytt beroende: byggtidseffekten anges i PR:en (skill `ci-budget`).

## Inloggning (android:firebase-auth)

Inloggning är **obligatorisk**: inget data läses utloggat, rules bygger på `request.auth.uid`.

- `AuthViewModel.state.gate`: `Loading | SignedOut | NeedsHousehold | UpdateRequired | Ready`; `AppRoot` visar `SignInScreen` tills det finns en användare.
- `AuthRepository` (`data/auth/`), enda implementationen `GoogleAuthRepository` (bunden i `di/AuthModule`), fel mappas i `data/auth/AuthErrors.kt`; tester använder `FakeAuthRepository`. `AuthUser(uid, name?, email?)` – namn och e-post visas bara i Inställningar (SET-4), aldrig fotot (skill `data-privacy-security`).
- Hushållssteget: när `authState` har en användare och `HouseholdSession.selection` är `null` väljer `EnsureHouseholdUseCase` äldsta hushållet där `memberUids` innehåller uid, annars skapas "Mitt hushåll". `HouseholdDirectory` (`data/household/`, Firestore-implementation i `data/firestore/`) frågar bara servern (`Source.SERVER`, HUS-1); `create` returnerar först när servern tagit emot (annars `Offline` efter 15 s).
- En knapp, ett event `AuthEvent.SignIn(context)`: utloggad → logga in; inloggad utan hushåll → försök hushållssteget igen. `busy` täcker båda; knappen visar "Loggar in …". Fel visas en gång via `AppSnackbarHost` och kvitteras med `AuthEvent.ErrorShown`; `Cancelled` är tyst (AUTH-2).
- Utloggad med valt hushåll kvar → `AuthViewModel` rensar valet. `gate = Loading` tills hushållets `schemaVersion` är känd; nyare än appen → `UpdateRequiredScreen` (HUS-3).
- Rules: `households` läses med `request.auth.uid in resource.data.memberUids`, så att `array-contains`-frågan går att bevisa.
- Läs aldrig Firestore innan `HouseholdSession` har ett värde.
- Utloggning bara i Inställningar bakom `ConfirmDialog` (SET-4). **Nuvarande läge, avviker från pluginen:** `SettingsViewModel` anropar `AuthRepository.signOut()` direkt (rensar Credential Manager, Firebase-sessionen och hushållsvalet), utan eget use case och utan att vänta in osynkade skrivningar och tömma cachen. Migreras enligt pluginen i en egen PR senare.
- SHA-1 för debug och release är registrerade i Firebase-projektet `reseapoteket` (README → "Firebase-setup").

## Intent-säkerhet (android:android-intent-security)

Används vid manifest, exporterade komponenter, extras, `PendingIntent`, notiser och delning.
Här gäller skill `data-privacy-security`: hälsodata, notiser privata på låsskärmen, delning
bara via `FileProvider`. Bara startskärmen exporteras (`ComponentExportTest`).

## Security rules (android:firebase-security-rules-auditor)

Varje ändring i `firestore.rules` granskas med den. Här gäller skill `firestore-data-layer`
(regler under `households/{hid}`, medlemmar i `memberUids`, tolerans för okända fält enligt
DAT-5) och skill `data-safety-backup`; rules-tester mot emulatorn (skill `testing-strategy`).

## Mockup (android:mockup)

Processen (när, hur, visa och vänta) står i pluginen; det här är ReseApotekets delar.

- **Mallen:** https://claude.ai/artifact/JuYKsRkwnrcbzXz4ccBodk – komponentark, beteendeark och
  navigationsramen, godkänd i #20. Varje mockup startar från den.
- **Designreferens:** packlistan i B/C-jämförelsen, https://claude.ai/artifact/6P25x1LRrkPBcF9BFqzi7p (väg C).
- **Facit är det byggda:** `ComponentGallery` och skärmdumparna i `app/src/test/screenshots/`.
  Ändras katalogen uppdateras mallen i samma PR som komponenten.
- **Byggs av** katalogen i skill `shared-ui-components`, i designspråket i skill `ui-style`;
  listor och formulär med ramarnas struktur (`EntityListScreen`, `EntityEditScreen`).
- **Data:** riktiga svenska texter och realistisk, påhittad data – familjemedlemmar med emoji,
  verkliga medicinnamn och doser, en resa med datum och antal dagar. Aldrig familjens riktiga
  recept eller hälsodata.
- **Länken** läggs i issuet under rubriken **Design** (skill `android:refine-issue`); koden skrivs
  först efter användarens ok (skill `android:implement-issue`).
- **I PR:en:** Roborazzi-skärmdumpar (ljust + mörkt) av det byggda bredvid mockup-länken
  (`./gradlew :app:recordRoborazziDebug` vid avsiktlig designändring); avvikelser förklaras.
- Mockupens HTML/CSS och färgvärden kopieras aldrig – översätt till `ui/theme` och delade komponenter.

## Arbetsflöde (android:implement-issue)

Stegen står i pluginen; det här är ReseApotekets delar.

- **Repo:** owner `ParTee71`, repo `ReseApoteket`, basbranch `master`. Issues och PR:er läses och
  skrivs med GitHub MCP.
- **Databasen i tester:** tester i `tools/db` körs bara mot Firebase-emulatorn, aldrig mot den
  riktiga databasen (skill `data-safety-backup`).
- **Domänskills:** datalagret → skill `firestore-data-layer`; beräkningar, mängder och
  `ReasonFormatter` → skill `packing-engine` (BER-krav); recept och loggning → skill
  `data-privacy-security`.
- **Lagerkedjan:** `Compose → ViewModel(StateFlow<UiState>, sealed events + onEvent()) →
  Repository → FirestoreCollection<T> + DocCodec<T>`, Hilt, fel mappas en gång till `DataError`
  i `data/common`. En listskärm är en `EntityListScreen`, ett formulär en `EntityEditScreen` +
  `EditorState` (skill `shared-ui-components`).
- **Datakedjan** för ett persisterat fält eller en ny samling: default i `:core`-modellen,
  tolerant `DocCodec`, `firestore.rules`, `tools/db/lib/collections.mjs` och rundturstest – i samma
  ändring (skill `data-safety-backup`).
- **Agenter:** huvudsessionen orkestrerar, implementerar (skärmar, komponenter, datamodell, codecs,
  rules, CI-filer) och granskar – repot har ingen `byggare` eller `arkitekt`. Agenten
  `testskrivare` tar tester på tre eller fler nivåer, `granskare` granskar diffen (steg 8),
  `ci-doktor` diagnostiserar rött CI och `db-inspektor` läser faktisk data. Modell och effort per
  del: avsnittet Issues nedan.
- **`/code-review` krävs** (efter `granskare`) när ändringen rör logik i `:core`, datalagret,
  `firestore.rules`, `tools/db` eller en delad ram eller komponent (`ui/components`, `ui/common`),
  eller när diffen är större än ett par filer.
- **Commit-områden:** `packlista`, `berakning`, `recept`, `resor`, `paminnelser`, `lakemedel`,
  `rules`, `data`, `ui`, `säkerhet`, `claude`, `skills` – t.ex. `feat(packlista): …`,
  `fix(berakning): …`.
- **Kommandon:** CLAUDE.md → "Bygg & test" och skill `testing-strategy` → "Innan du anser dig
  klar". Manifest, resurser, proguard eller byggfiler ändrade → `./gradlew :app:lintDebug
  :app:assembleRelease` lokalt före push (körs inte i PR-flödet, skill `ci-budget`).
- **I PR:en:** Roborazzi-skärmdumpar (ljust + mörkt) bredvid mockup-länken för GUI, versionen ur
  `version.properties`, och byggtidseffekten vid nya beroenden.
- **Emulatortesterna:** jobbet `instrumented` körs i PR:er som rör filtret `instrumented`, och
  `ci-ok` fäller PR:en om det blir rött eller avbryts; ett överhoppat jobb fäller inte (skill
  `ci-budget`).

## Issues (android:refine-issue)

- **Repo:** owner `ParTee71`, repo `ReseApoteket`; dubblettsökning med
  `mcp__github__search_issues` (`repo:ParTee71/ReseApoteket <ord>`).
- **Extra undersökningspunkter:** berörs en formel → skill `packing-engine` och BER-kraven; loggas,
  lagras eller exporteras receptdata (hälsodata) → skill `data-privacy-security`.
- **Datakedjan i DoD:** modell med default, codec, `firestore.rules`, `tools/db/lib/collections.mjs`
  och rundturstest – eller "ingen persisterad ändring".
- **Testplanens nivåer:** `:core` (ren JUnit), `app/src/test` (ViewModel med fakes + Turbine,
  Robolectric, kontraktstester för ramarna, Roborazzi-skärmdumpar), `tools/db` (rules och rundtur
  mot Firebase-emulatorn), `app/src/androidTest` (instrumenttester, i Actions).
- **Verifiering**, exempel: `./gradlew :core:test --tests "*PackingEngine*"`, vilka Roborazzi-bilder
  som ändras, eller `firebase emulators:exec --only firestore "node --test 'tools/db/test/*.test.mjs'"`.
- **Etiketter:** `funktion`, `arkitektur`, `ai-regler`, `prio:1`–`prio:3`, `epic`.
- **Agent · modell · effort** (obligatoriskt även här). Repot har bara dessa:

  | Agent | Tar | Standard |
  |---|---|---|
  | huvudsessionen | implementationen – skärmar, komponenter, datamodell, codecs, rules, CI | Opus · medium; datamodell, rules, migrering och backup-kedjan minst Opus · high |
  | `testskrivare` | tester när tre eller fler nivåer berörs | Sonnet · medium |
  | `granskare` | granskningen i steg 8, alltid med | sessionens modell (Opus) · medium |
  | `ci-doktor` | rött CI – startas vid behov, anges inte i förväg | Sonnet · medium |
  | `db-inspektor` | faktisk data, bara läsning – anges när issuet kräver det | Haiku · low |

  Minst två rader: huvudsessionen och `granskare`. Modellen följer tabellen i
  `android:refine-issue`; standarden ovan är agentens frontmatter (`granskare` ärver sessionens modell).

## Release (android:release)

- **Versionsfilen** är `version.properties` (`versionCode`, `versionName`) i roten, utanför
  CI-filtren; README:s rad står under "Versionshistorik". Båda höjs i funktions-PR:en.
- **Workflow:** `release.yml` (owner `ParTee71`, repo `ReseApoteket`), jobben:
  - `gate` (sekunder): commiten ligger på `master`, versionen är `versionName` i
    `version.properties`, taggen finns inte, secrets finns och `ci-ok` är grönt på commiten –
    master-pushen har kört alla tester, `cpdCheck`, rules-tester och dokumentkontroller, och de
    körs inte om. Starta releasen först när CI på `master` är klart, annars stoppar `gate` den.
  - `build`: lint och det signerade, minifierade bygget i en Gradle-körning, parallellt med
  - `instrumented` (`instrumented.yml`): hoppas över när sökvägarna i filtret `instrumented` är
    oförändrade sedan en PR-commit där de var gröna (`.github/scripts/instrumented-paths.mjs`).
  - `publish`: när båda är gröna – deployar `firestore.rules`, skapar taggen och GitHub Release.
- **Secrets:** `GOOGLE_SERVICES_JSON`, `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`,
  `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`, `FIREBASE_RULES_DEPLOYER`.
- **Rules:** `publish` deployar `firestore.rules` (secret `FIREBASE_RULES_DEPLOYER`) före GitHub
  Release, bara om de ändrats sedan förra `v*`-taggen; en publicering vars `firestore.rules`
  skiljer sig från `master` stoppas. Skapades en tagg utan att `publish` lyckades: kör `rules.yml`
  med `deploy: true` om rules ändrats.
- **Läkemedelslistan före release (REC-6):** `node tools/medicines/build.mjs` hämtar
  Läkemedelsverkets lista och skriver `app/src/main/assets/medicines.tsv` (stoppar hellre än
  skriver en trasig lista). Ändrades filen landar den i en liten PR före releasen – den kör
  app-testerna och instrumenttesterna (sökvägen ligger under `app/src/`) – med "uppdaterad
  läkemedelslista" på README-raden, och patch-höjning om versionen inte redan höjts sedan senaste
  tagg. Trigga releasen när den är mergad och CI på `master` är grönt.
- **Kravlistan:** inga kvarvarande `*(planerad)*` för byggda funktioner, och TP-kraven matchar
  SDK-nivåerna i `app/build.gradle.kts`.
- **Har `schemaVersion` höjts sedan förra releasen:** nämn det tydligt (skill
  `data-safety-backup`) och be användaren göra detta, i den här ordningen, **innan** release-bygget
  triggas:
  1. deploya `firestore.rules` med den nya `maxSchemaVersion()` – kör `rules.yml` med
     `deploy: true` (`publish` deployar dem också, men stämplingen måste vänta på dem);
  2. stämpla hushållen: `node tools/db/migrate.mjs --to <N> --dry-run`, sedan utan `--dry-run`.

  Äldre appar visar då "Uppdatera appen" (HUS-3) redan innan den nya appen kan skriva ett värde
  som de skulle läsa fel och skriva tillbaka. Appen stämplar själv bara när inget steg ändrar data
  (`SchemaMigrator.canStamp`) – och bara om de nya rules redan är deployade. Vänta tills
  användaren bekräftat båda.
- **Dispatch:** `workflow_id: release.yml`, `ref: master`,
  `inputs: { version_name: "<version>", publish_release: "true" }` – `version_name` utan `v`,
  `publish_release` som sträng. Med `"false"` blir det ett testbygge utan tagg och Release: APK:n
  ligger då bara som artifacten `reseapoteket-v<version>` – länka den.
- **APK:n** heter `ReseApoteket-<version>-<ÅÅÅÅMMDD-HHMM>.apk` (NFR-17); sammanfattningen börjar
  `Släppt: ReseApoteket v<version>`.
- **Lokal reserv:** `./gradlew :app:assembleRelease` kräver keystore i `app/` och
  signeringslösenord via `local.properties` eller `SIGNING_*`-miljövariabler (skill
  `data-privacy-security`).
