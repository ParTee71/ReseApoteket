---
name: shared-ui-components
description: Regel 4 i ReseApoteket — samma elementtyp ser ut och beter sig likadant överallt och varje sak finns på ett ställe. Enda källan för komponent-, ram- och byggstenstabellerna och förbudslistan. Ladda alltid vid UI, skärmar, komponenter, interaktionsmönster, formulär, listor, formatterare eller liknande kod på två ställen. Trigger-ord: komponent, skärm, knapp, kort, dialog, snackbar, tomt tillstånd, laddning, arkivera, ångra, radera, bekräfta, redigera, spara, validering, dubbelkod, återbruk, EntityListScreen, EntityEditScreen, EditorState, UiConsistencyTest, cpdCheck, allowlist.
---

# Enhetligt och utan dubbelkod

**Invariant:** en elementtyp = en komponent, ett interaktionsmönster = en ram, en sorts
logik = ett ställe. Två skärmar som visar eller gör samma sak ska se ut och bete sig
identiskt. Skillnader uttrycks **bara** med parametrar på den delade delen.

Denna skill är **enda källan** för tabellerna nedan. `CLAUDE.md`, andra skills och
agenter länkar hit – de kopierar dem inte. Komponenttabellen och ramtabellen läses av
`UiConsistencyTest` och jämförs med koden (se *Dokumentdrift*). Håll formatet: en rad per
komponent, komponentnamnet i backticks först i kolumn två.

> Rader märkta *(android-ui)* är komponenter ur det gemensamma biblioteket
> [android-ui](https://github.com/ParTee71/android-ui) (`se.partee71.ui.components`, sedan #126) –
> appen har ingen egen kopia, och en ändring görs i biblioteket. Temat (`AppTheme`, `Spacing`,
> `AppShapes`, `AppTypography`, `ExtendedColors`, `Tone`) kommer också därifrån (skill `ui-style`).
> `UiConsistencyTest` kontrollerar att de finns i biblioteket och i `ComponentGallery`.
>
> Hela katalogen och ramarna finns sedan #20 och syns i `ComponentGallery` (Inställningar →
> Komponenter i debug-bygget). De exakta API:erna bakom kolumnen *Bygger på*
> står i `ARKITEKTUR.md` → "Verifierade API:er" – gissa aldrig ett API-namn.

## Utseende: elementtyp → enda tillåtna komponent

| Elementtyp | Komponent | Bygger på |
|---|---|---|
| Knapp (primär/sekundär/text/destruktiv) | `AppButton` *(android-ui)* | M3-knappar, `variant`- och `compact`-parameter |
| Ikonknapp | `AppIconButton` *(android-ui)* | M3 ikonknapp, min 48 dp |
| Split-knapp "Lägg till ▾" | `AddSplitButton` *(android-ui)* | Expressive split-knapp; menyn bakom pilen är `AppMenu`s (`menu = items.asSplitMenu()`) |
| Kort / grupperad yta | `ApotekCard` | `AppTheme.shapes.card`, `Spacing`; `tone` färgar ytan (grönt när allt är packat) |
| Listrad | `ItemRow` | bas för alla listor; `navigates` ger pil (efter `trailing`), `done` packad-stil, `accent` personens färg, `inactive` nedtonad (pausat), `below` en rad under (resenärer och packat), `titleHighlight` färgmarkerar en del av titeln (sökträff); titeln högst två rader, och kapas den visar långtryck hela titeln och undertexten i en bubbla (NFR-20) |
| Resenärer på en resa | `AvatarStack` | överlappande `PersonAvatar` (Small) med ring; TalkBack läser namnen |
| Pausbar rad (recept, kosttillskott, familjeartikel) | `PausableRow` | `ItemRow` + typ som `InfoPill`; pausad nedtonad med "Pausat" |
| Kryssrad | `CheckRow` | `ItemRow` + formmorfande kryss; med `onClick` är krysset en egen knapp (läses med namn och mängd) och raden öppnar detaljer; `accent` = personens färg |
| Sektionsrubrik med räknare | `SectionHeader` | `InfoPill` för räknaren |
| Gruppetikett över kort | `GroupLabel` | versal liten text, läses som rubrik |
| Formulärfält med etikett (val, väljare, dosering) | `LabeledGroup` | `GroupLabel` ovanför innehållet, valfri `helper` under och fältfel (`error`, `FieldError`) |
| Fel under ett fält eller i en panel | `FieldError` | Felfärg, läses som fel; ett skrivfel i ett bottom sheet visas via `AppBottomSheet(error)` |
| Skärmrubrik | `AppTopBar` *(android-ui)* | Expressive flexibel toppbar, `size`-parameter; före `actions` står appens synkindikator (`InfoPill` med moln, `SyncIndicator.topBarStatus()` satt som `LocalTopBarStatus`) när ändringar väntar (NFR-6) |
| Toppnivånavigering | `AppFloatingToolbar` | Expressive flytande verktygsrad; `LocalBottomClearance` ger plats under den |
| Textfält | `AppTextField` | M3 textfält med fel/hjälptext; flerradigt växer med texten upp till `maxLines` |
| Textfält med förslag (läkemedelslistan, REC-6) | `SuggestionField` | `AppTextField` + kort med en `ItemRow` per `Suggestion` (matchningen färgmarkerad via `titleHighlight`) och valfri källrad; antalet läses upp; anroparen bestämmer vad ett val fyller i |
| Etikett/pill | `InfoPill` | `AppTheme.shapes.pill`; `icon` före texten, `onClick` gör den till en knapp med 48 dp tryckyta |
| Personchip | `PersonChip` | M3 filterchip + `PersonAvatar` |
| Valchip | `AppFilterChip` | M3 filterchip |
| Ett val bland några (form, enhet, kategori) | `ChoiceChips` | `ChipRow` + en `AppFilterChip` per alternativ |
| Exempel att börja från (snabbval, tom lista) | `ExampleChips` | `ChipRow` + `AppFilterChip` med plus; det valda med bock; varje chip väljer sitt eget objekt (`label` ger texten) |
| Avatar | `PersonAvatar` | emoji i färgad cirkel, `size` (`Small`, `Regular`, `Large`) |
| Emojival | `EmojiPicker` | rutnät av runda val (`ChoiceGrid`), kurerad lista `PERSON_EMOJIS` |
| Färgval | `ColorSwatchPicker` | rutnät av runda val (`ChoiceGrid`) över `AppColors.PERSON_HEX`; TalkBack läser färgens namn |
| Antal ± | `QuantityStepper` | M3 ikonknappar i en pill, `label` för TalkBack |
| Dosering | `DoseScheduleRow` | `AppFilterChip` + `QuantityStepper`; valideringsfel (`error`) under fälten |
| Mängdregel | `QuantityRuleRow` | `AppFilterChip` + `QuantityStepper`; valideringsfel (`error`) under fälten |
| Packprogress | `SuitcaseProgress` | Expressive vågig progress, `compact`-parameter; allt packat → "Allt packat! 🧳" |
| Laddning | `AppLoading` *(android-ui)* | Expressive laddningsindikator |
| Tomt tillstånd | `EmptyState` | ikon + text + `AppButton` |
| Bekräftelse | `ConfirmDialog` | M3 dialog, `destructive`-parameter |
| Bottom sheet | `AppBottomSheet` | M3 modal sheet; scrollar när innehållet är högre än skärmen |
| Meddelande | `AppSnackbarHost` *(android-ui)* | M3 snackbar; ramarna har var sin för skärmens åtgärder, `AppNavHost` en för appens synkmeddelanden (NFR-6) – ett pågående meddelande ersätts aldrig av ett annat |
| Firande | `Confetti` *(android-ui)* | egen `Canvas`, inget bibliotek; färgerna är personfärgerna (`ExtendedColors.accents`) |
| Avdelare | `AppDivider` *(android-ui)* | M3-avdelare med temats färg och tjocklek |
| Inställningsrad med växel | `SwitchRow` | `ItemRow` + M3-växel; med `onClick` är växeln en egen kontroll och raden öppnar detaljer (som `CheckRow`) |
| Segmentval (t.ex. tema) | `AppSegmentedChoice` *(android-ui)* | M3 segmentknappar |
| Meny (arkivera/radera m.m.) | `AppMenu` | M3 rullgardinsmeny |
| Datumfält | `DateField` | `AppTextField` + M3 datumväljare |
| Tidsfält | `TimeField` | samma yta som `DateField` + M3 tidsväljare (24 timmar); valfri `helper` |
| Packas på resedagen (recept, kosttillskott, familjeartikel, egen rad) | `TravelDayField` | `SwitchRow` + `TimeField`; resedagens morgon (`LocalTravelDayMorning`, telefonens tid) tills en egen tid väljs |
| Horisontell rad av chips | `ChipRow` *(android-ui)* | horisontell lista av `PersonChip`/`AppFilterChip` |
| Förhandsvisning (mängd, nästa påminnelse) | `PackingPreview` | `ReasonFormatter` i `:core`; samma ruta för "Nästa påminnelse" |

## Beteende: interaktionsmönster → enda tillåtna ram

| Mönster | Ram | Beteende (identiskt överallt) |
|---|---|---|
| Listskärm | `EntityListScreen` | Arkivering via `archive: ListArchive` ("Visa arkiverade" i menyn, Ångra, fel som meddelande); laddning → `AppLoading`; tomt → `EmptyState` med primärknapp och valfria exempel (`EmptyContent.examples`, `ExampleChips`); fel → feltillstånd (`EmptyState` med `isError`) och "Försök igen"; rader (`ItemRow`) i kort; valfri gruppering (`SectionHeader` med antal eller `ListGroup.count`, `columns` = upp till 2 kolumner, bara så många som får plats med minst 260 dp var (NFR-20), `collapsible` = hopfälld) och undergrupper (`ListSubgroup`, personens namn i färg); lägg till (`AddSplitButton`) ovanför verktygsraden; för en undersida `onBack`, `subtitle`, `actions` och `header` överst i listan; `ListArchive.showToggle = false` utan "Visa arkiverade" |
| Redigeraskärm | `EntityEditScreen` | Tillsammans med `EditorState<T>`: "Spara" aktiv först när giltig och ändrad (`saveLabel`-parameter); fältfel visas när fältet ändrats eller efter ett sparförsök; läsfel → feltillstånd med "Försök igen"; bakåt med osparat → "Släng ändringar?"; sparfel → snackbar; lyckad sparning → tillbaka (eller `onSaved`, t.ex. till packlistan för en ny resa); båda kvitteras via `events` (formulärets `EditorState`) och visas inte igen efter rotation; arkivera/återställ/radera i menyn |
| Detaljskärm | `EntityDetailScreen` | Tillsammans med `DetailUiState`/`DetailLoader`: toppbar med tillbaka, "Redigera" och meny (arkivera/återställ); laddning → `AppLoading`; fel → läsfel med "Försök igen" (samma som listan och redigeringen); innehåll → centrerat huvud (bild, namn, rad under) och sektioner i `ApotekCard` med `SectionHeader`; fel från en åtgärd (`error`) som meddelande |
| Arkivera/dölj | `SwipeToHide` *(android-ui)* | Svep → dolt direkt → `UndoSnackbar` "%s arkiverad · Ångra" i 5 s (packlistan: "%s dold" via `ArchiveActions(undoFormat)`); `enabled = false` för en redan arkiverad rad |
| Ångra | `UndoSnackbar` | Enda ångra-mekanismen |
| Permanent radering | `ConfirmDialog` | `destructive = true`; alltid bekräftelse, aldrig svep. `onDecline` när nej-knappen gör något annat än att stänga ("Behåll listan") |
| Förhandsvisning av beräkning | `PackingPreview` | Samma format i recept, kosttillskott, familjeartiklar |
| Komponentöversikt | `ComponentGallery` | Debug-skärm med alla komponenter och ramar i alla tillstånd |

Gemensamt för ramarna: felmeddelanden via `DataError.toMessage()`, skärmbyten via
`navigation/Transitions`, återkommande texter ("Spara", "Släng ändringar?", "Ångra",
"Försök igen", "Inget här än") en gång i `strings.xml`.

## Kod: delade byggstenar i andra lager

| Behov | Enda stället |
|---|---|
| Firestore-CRUD | `EntityCollection<T>` från `CollectionFactory` (`data/common/`); enda implementationen `FirestoreCollection<T>` i `data/firestore/`, i test `FakeCollection<T>` |
| Sökvägar, codecs per samling | `Paths` och `CollectionTable` i `data/firestore/` (speglade i `tools/db/lib/collections.mjs`) |
| Map ↔ modell | `DocCodec<T>` + fälthjälpare i `:core` |
| Formulär | `EditorState<T>` + `Validator<T>` i `ui/common/` (fel per fält visas efter ändring eller sparförsök; klar och sparfel som `EditorUiState.done`/`failure`, kvitterade via `EditorEvents`) |
| Listtillstånd | `Flow<List<T>>.asListUiState()` → `ListUiState<T>`, och `ListLoader` (med "Försök igen") + `activeOnly()` för en listas ViewModel, i `ui/common/` |
| Svep-arkivera, Ångra, "Visa arkiverade" i en lista | `ArchiveActions` + `ArchiveEvent` i `ui/common/` (en per list-ViewModel, exponerad som `archive`); skärmen skickar `archive.collectAsListArchive()` till `EntityListScreen`. Ångra för något annat än arkivering (t.ex. rader som just lagts till, PAK-7) erbjuds med `offerUndo(request) { … }` – samma snackbar |
| Detaljtillstånd | `Flow<T?>.asDetailUiState()` → `DetailUiState<T>`, och `DetailLoader` (med "Försök igen") för en detaljskärms ViewModel, i `ui/common/` |
| ViewModel med nyckel | Hilt assisted injection: `@HiltViewModel(assistedFactory = …)` + `hiltViewModel<VM, VM.Factory> { it.create(key.…) }` i `appEntries` (ARKITEKTUR → Verifierade API:er) |
| Datum i UI | `DateFormat` i `ui/common/` ("lör 19 dec 2026", millis för datumväljaren) |
| Visa först efter en fördröjning (indikatorer som inte ska blinka) | `Flow<Boolean>.shownAfter(delay)` i `ui/common/` |
| Navigation | `AppKey`, `AppBackStack`, `AppNavHost`, `Transitions` i `navigation/` |
| Feltyp | `DataError` (`Offline`, `PermissionDenied`, `Cancelled`, `UpdateRequired`, `SignInRejected`, `NoHousehold`, `NotFound`, `Unknown`) i `data/common/` |
| Fel till text | `DataError.toMessage()` i `ui/common/`; `NotFound` (raderat) visas utan "Försök igen"; som meddelande via `ErrorSnackbar` (`template` sätter felet i ett sammanhang) |
| Arkivera | `Archivable` + `FirestoreCollection.setArchived` |
| Skriva bara vissa fält (resans status, en rads avprickning) | `EntityCollection.update(item, fields)` / `updateAll` (atomärt) med fältnamnen som konstanter i codecen (`TripCodec.STATUS`, `PackingItemCodec.CHECKED`); se skill `firestore-data-layer` |
| Beräkningar | `PackingEngine` i `:core` |
| Packlistans ordning, dolda, ändrad mängd och resans status | `PackingList` i `:core` (`arrange`, `isChanged`, `amount`, `statusAfter`) |
| Packförslag för en resa (förhandsvisning, spara med packlista, räkna om) | `TripPacking` i `domain/` – enda stället som hämtar underlaget till `PackingEngine`; `save` läser allt före skrivning, `recount` skriver bara listan |
| Svensk sortering av namn | `SwedishOrder.names` i `:core` |
| Mängd-, doserings- och regeltexter | `ReasonFormatter`, `DoseScheduleFormatter`, `QuantityRuleFormatter` i `:core` |
| Lista + data per nyckel ur den (recept per person, packlista per resa) | `combineByKey` i `data/common/` – källan följs en gång, flödet för nyckeln byts bara när nyckeln ändras |
| Tillägg som inte får fälla skärmen (avatarer, packat, sammanfattning) | `withFallback(värde)` i `data/common/FlowRetry.kt` – ersättning vid fel och nytt försök efter paus |
| Ordning för något nytt | `upsertPlaced`/`nextSortOrder` i `data/common/` – ur cachen, väntar inte på nätet |
| Resultat av suspend-anrop | `suspendRunCatching` i `data/common/` (används av `FirestoreCollection` och `AuthRepository`) |
| Firestore-instans | `FirestoreModule` (Hilt) i `data/firestore/` |
| Aktivt hushåll | `HouseholdSession` i `data/household/` (`HouseholdScope`) |
| Påminnelser (plan, genomgång, notiser, schemaläggning) | `ReminderPlanner` i `:core`; `reminders/` i appen (`ReminderSource`, `ReminderRunner`, `ReminderNotifier`, `ReminderScheduler`) – skill `firestore-data-layer` för läsningen |
| Synkläge | `SyncStatus` (`syncing`, `lastWriteError`) i `data/common/`; för UI:t `SyncViewModel` (`ui/sync/`) → `AppNavHost` (indikatorn via `LocalTopBarStatus`, nekad skrivning som meddelande ovanför verktygsraden) |
| Läs- och skrivregler för dokument | `prepareForWrite`, `readDocument`, `sortForList` i `data/common/DocumentRules.kt` och `writeBlocker` i `HouseholdScope.kt` – används av både `FirestoreCollection` och `FakeCollection` |
| Inställningar | `SettingsStore` (DataStore) |
| Hushållets namn och marginaldagar | `HouseholdRepository.update(household, fields)` med `HouseholdCodec.NAME`/`MARGIN_DAYS` – aldrig hela hushållet |
| Export av hushållet | `HouseholdExport` + `ExportFiles` (`data/export/`), formatet i `ExportFormat` (`:core`) – se skill `firestore-data-layer` |
| Test-fakes och hjälpare | se skill `testing-strategy` |
| `tools/db`-logik | `tools/db/lib/` (admin, collections, walk, serialize, cli) |
| CI-steg | composite actions i `.github/actions/` |
| Byggkonfiguration | ett ställe (skill `android:android-gradle-logic` + `android-projekt`) |

## Förbjudet

Kontrolleras av `UiConsistencyTest` (Konsist) i bygget och av hooken `regel4-check`
direkt efter varje filändring. De exakta mönstren står **bara** i
`app/src/test/resources/ui-forbidden.txt` (enda källan, `mönster<TAB>ersättning`), och
undantag bara i `ui-allowlist.txt`. Att hooken och testet tolkar filen likadant bevisas av
`ui-forbidden-examples.txt`, som båda kör – ändras ett mönster, lägg till ett exempel där. I korthet:

- **Feature-kod** (Kotlin i ett ui-paket utom `ui/components`, `ui/theme`, `ui/common` –
  även filer direkt under `ui/`):
  - Material 3-komponenter och layoutelement som har en motsvarighet i tabellerna ovan –
    t.ex. kort, knappar, kryss, växlar, fält, menyer, dialoger, sheets, avdelare, chips,
    progress, snackbar, `LazyColumn`/`LazyRow`, `Scaffold`, `SwipeToDismissBox`.
  - Hårdkodat utseende: färger, former, textstilar (även `MaterialTheme.typography` – använd
    `AppTypography`) och literala dp för storlek och padding.
  - **Tillåtet:** `Text` med en `AppTypography`-stil, `Icon` (appens ikoner i `res/drawable`),
    `Column`/`Row`/`Box` och `Spacer` med `Spacing`.
- **All kod utom `ui/theme/` och `ui/components/`:** `ExperimentalMaterial3ExpressiveApi` (opt-in
  för Expressive). Behövs ett Expressive-API i en skärm → bygg eller utöka en delad komponent.
- **All kod utom `data/firestore/`** – även `di/` och `ui/`: `FirebaseFirestore`,
  `CollectionReference`, `DocumentReference`. Därför ligger `FirestoreModule` i `data/firestore/`.
- **All kod utom `di/`:** `Dispatchers.IO`/`Default`/`Main`/`Unconfined`. Klassen tar en
  `CoroutineDispatcher` med `@IoDispatcher` eller `@DefaultDispatcher` från `di/DispatchersModule`
  (skill `android:kotlin-coroutines`).
- **All kod i `ui/`** – även `ui/components/` och `ui/common/`: `Channel(` och `.receiveAsFlow(`
  för engångshändelser. Händelsen blir ett fält i tillståndet som skärmen kvitterar
  (`EditorUiState.done`/`failure` + `EditorEvents`; skill `android-projekt`, Flöden).
- **ViewModels** som heter `*EditViewModel` och inte exponerar en `EditorState`.
- **UiState-typer** (data classes som heter `*State` och sealed interfaces `*UiState` i `ui/`)
  utan `@Immutable` eller `@Stable`. Listor och kartor i dem är `ImmutableList`/`ImmutableMap`;
  modellerna från `:core`, `DataError` och datum-/tidstyperna räknas som stabila via `app/compose-stability.conf` (skill `android:compose-expert`).
- **I `ui/components/`:** publik komponent utan skärmdump (ljust + mörkt) eller utan plats i
  `ComponentGallery`.
- **Undantag:** en rad i `ui-allowlist.txt` (`fil:symbol – motivering`). Undantaget gäller bara
  träffar vars matchade text överlappar symbolen – andra förbjudna mönster på samma rad stoppas
  ändå. En rad utan symbol eller motivering fäller hooken och bygget. Filen är tom som
  standard. Varje ny rad kräver användarens uttryckliga ok i PR:en.

## Dokumentdrift

`UiConsistencyTest` läser komponenttabellen och ramtabellen i denna fil och jämför med
de publika `@Composable`-funktionerna i `ui.components`:
- komponent i koden men inte i tabellen → bygget faller ("lägg till raden i shared-ui-components");
- komponent i tabellen men inte i koden → bygget faller ("ta bort raden eller bygg komponenten").

En ändring av denna fil kör därför app-testerna i CI (skill `ci-budget`).

## `cpdCheck` (copy-paste-detektor)

PMD CPD för Kotlin över `core/src`, `app/src/main`, `app/src/test`, `app/src/sharedTest` och `app/src/androidTest`, tröskel ca 80
tokens (satt i #11). Fäller bygget. En träff betyder: bryt ut till en av byggstenarna
ovan eller utöka en ram. Tröskeln höjs inte för att "det bara är lite likt".

## Arbetsflöde

1. Slå upp elementtypen, mönstret eller behovet i tabellerna. Finns det → använd det.
2. Räcker det inte → **utöka** med en parameter vars default bevarar nuvarande utseende
   och beteende. Uppdatera bara de skärmdumpar som avsiktligt ändras.
3. Något nytt behövs → ny delad del i rätt paket, rad i tabellen, test, skärmdump, plats i
   `ComponentGallery` – och visad i mockup först (skill `android:mockup` + `android-projekt`).
4. Delade delar utökas i en egen commit **före** featuren som behöver dem.
5. Två ställen gör nästan samma sak → bryt ut innan det tredje kommer.
6. En ändring av en elementtyp eller ett mönster görs på ett ställe och slår igenom överallt.

## Anti-mönster

- Lokal `Card { }` eller `Button { }` "bara här".
- Kopierad `CheckRow` med annan färg i en feature-mapp.
- Radering som bekräftas på ett ställe men sker direkt på ett annat.
- Egen `LazyColumn` med eget tomt tillstånd.
- Repository som anropar Firestore direkt.
- Två ViewModels med samma dirty/validera/spara-logik.
- Samma felmeddelande eller mängdtext formulerad på två sätt.
- En regeltabell kopierad till en annan fil i stället för en länk hit.
