---
name: android-projekt
description: ReseApotekets anpassningar av de generella android:-skillsen från pluginen android@claude-android (android-dev, kotlin-coroutines, kotlin-flows, compose-expert, navigation-3, accessibility-compose, android-gradle-logic, firebase-auth, android-intent-security, firebase-security-rules-auditor, mockup). Ladda den alltid tillsammans med en android:-skill – den säger vad som gäller just här och går före pluginens generella råd. Täcker projektets klasser och mönster, nuvarande avvikelser från pluginen, Gradle-uppsättningen, inloggning med hushåll, a11y-detaljer för appens komponenter och mockup-processen med mallens länk. Trigger-ord: Android, Kotlin, ViewModel, Hilt, Compose, coroutine, dispatcher, flöde, StateFlow, händelse, effekt, navigering, flik, tillgänglighet, TalkBack, Gradle, beroende, inloggning, logga in, logga ut, hushåll, google-services, SHA-1, manifest, Intent, firestore.rules, mockup, skiss, ny skärm, ny komponent, Design-canvas.
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
- **Nuvarande läge, avviker från pluginen:** targetSdk 35 (pluginen säger 36), inga `ImmutableList` i `UiState`, `SavedStateHandle` i stället för assisted injection, ingen Spotless/ktlint, Renovate eller Baseline Profile. Tas i egna PR:er senare; ny kod följer pluginen där det går.
- Java 17 (samma som CI). Tester JVM först (JUnit, Turbine, Robolectric, Roborazzi), fakes byggda på `FakeCollection<T>` (skill `testing-strategy`).

## Coroutines (android:kotlin-coroutines)

- Firestore är redan main-safe: `await()` och `snapshots()` blockerar inte – ingen `withContext` runt dem. `FirestoreCollection` är enda stället som anropar Firestore och hanterar fel och `CancellationException`.
- `suspendRunCatching` finns en gång i `data/common/`; skriv ingen egen variant.
- `:core` är ren och synkron – inga coroutines eller dispatchers där.
- ViewModel-tester använder den delade `MainDispatcherRule` (`StandardTestDispatcher` via `Dispatchers.setMain`).
- **Nuvarande läge, avviker från pluginen:** `Dispatchers.` nämns i dag även utanför `di/` – `data/export/ExportFiles.kt` och `data/medicines/MedicineRepository.kt` (`IO`), `data/firestore/HouseholdExporter.kt` (`Default`) och default-scopet i `data/datastore/SettingsStore.kt`. Repot ska migrera enligt pluginen (dispatcher injiceras bara där kod väljer en, och bara `di/` nämner `Dispatchers`) i en egen PR senare; ny kod följer pluginen.

## Flöden (android:kotlin-flows)

- `AuthRepository.authState: Flow<AuthUser?>` – `callbackFlow` på `FirebaseAuth.AuthStateListener`.
- `FirestoreCollection<T>.observe(): Flow<List<T>>` – `snapshots()` via `DocCodec<T>`, enda källan till dataflöden; repositories exponerar dem oförändrade eller kombinerade.
- `FirestoreCollection.syncing: Flow<Boolean>` (`hasPendingWrites`) driver appens enda "synkas…"-indikator.
- `Flow<List<T>>.asListUiState()` – gemensam omvandling till `ListUiState<T>` för alla listskärmar.
- `HouseholdSession.activeHouseholdId: StateFlow<String?>` – `flatMapLatest` på den när hushållet byts.
- `SettingsStore` – DataStore-flöden för tema och packlisteinställningar.
- ViewModels: `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)`.
- **Nuvarande läge, avviker från pluginen:** fel är redan state med kvittens (`ErrorSnackbar` + `…ErrorShown`), men engångseffekter går via `Channel` – `EditorState.effects` (`Channel(BUFFERED)`, konsumeras av `EntityEditScreen`), `PackingListViewModel.celebrations` (`CONFLATED`) och delningen i `SettingsViewModel`. Repot ska migrera till händelser i `UiState` enligt pluginen (Googles råd) i en egen PR senare; tills dess följer skärmar ramarnas nuvarande mönster.

## Compose (android:compose-expert)

- Ordning: skill `shared-ui-components` (regel 4) går före allt, sedan `ui-style` (färg, typografi, form, rörelse, texter). Skärmdumpar och kontraktstester: `testing-strategy`.
- Tema: `MaterialExpressiveTheme`, `MotionScheme.expressive()`, `AppTypography`, `AppShapes`, `Spacing`.
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
- **Länken** läggs i issuet under rubriken **Design** (skill `refine-issue`); koden skrivs
  först efter användarens ok (skill `implement-issue`).
- **I PR:en:** Roborazzi-skärmdumpar (ljust + mörkt) av det byggda bredvid mockup-länken
  (`./gradlew :app:recordRoborazziDebug` vid avsiktlig designändring); avvikelser förklaras.
- Mockupens HTML/CSS och färgvärden kopieras aldrig – översätt till `ui/theme` och delade komponenter.
