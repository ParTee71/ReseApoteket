---
name: shared-ui-components
description: ReseApotekets återbruksregel (regel 4) — samma typ av element ser likadant ut och beter sig likadant överallt, och varje sak finns på ett ställe. Enda källan för komponent-, ram- och byggstenstabellerna och förbudslistan. Ladda denna ALLTID när du bygger eller ändrar UI, en skärm, en komponent, ett interaktionsmönster, en ViewModel med formulär eller lista, ett repository, en formatterare eller när du ser liknande kod på två ställen. Trigger-ord: composable, komponent, UI, skärm, vy, knapp, kort, lista, listrad, kryss, fält, dialog, bottom sheet, snackbar, tomt tillstånd, laddning, arkivera, ångra, radera, bekräfta, redigera, spara, validering, återbruk, dubbelkod, duplicering, kopiera, likadant, enhetligt, konsekvent, beteende, mönster, ram, EntityListScreen, EntityEditScreen, EditorState, FirestoreCollection, UiConsistencyTest, cpdCheck, allowlist.
---

# Enhetligt och utan dubbelkod

**Invariant:** en elementtyp = en komponent, ett interaktionsmönster = en ram, en sorts
logik = ett ställe. Två skärmar som visar eller gör samma sak ska se ut och bete sig
identiskt. Skillnader uttrycks **bara** med parametrar på den delade delen.

Denna skill är **enda källan** för tabellerna nedan. `CLAUDE.md`, andra skills och
agenter länkar hit – de kopierar dem inte. Komponenttabellen och ramtabellen läses av
`UiConsistencyTest` och jämförs med koden (se *Dokumentdrift*). Håll formatet: en rad per
komponent, komponentnamnet i backticks först i kolumn två.

> Komponenterna och ramarna byggs i #20. Kolumnen *Bygger på* fylls i från
> `ARKITEKTUR.md` → "Verifierade API:er" (#11) – gissa aldrig ett API-namn.

## Utseende: elementtyp → enda tillåtna komponent

| Elementtyp | Komponent | Bygger på |
|---|---|---|
| Knapp (primär/sekundär/text) | `AppButton` | M3-knappar, `variant`-parameter |
| Ikonknapp | `AppIconButton` | M3 ikonknapp, min 48 dp |
| Split-knapp "Lägg till ▾" | `AddSplitButton` | Expressive split-knapp |
| Kort / grupperad yta | `ApotekCard` | `AppShapes.card`, `Spacing` |
| Listrad | `ItemRow` | bas för alla listor |
| Kryssrad | `CheckRow` | `ItemRow` + formmorfande kryss |
| Sektionsrubrik med räknare | `SectionHeader` | `InfoPill` för räknaren |
| Skärmrubrik | `AppTopBar` | Expressive flexibel toppbar, `size`-parameter |
| Toppnivånavigering | `AppFloatingToolbar` | Expressive flytande verktygsrad |
| Textfält | `AppTextField` | M3 textfält med fel/hjälptext |
| Etikett/pill | `InfoPill` | `AppShapes.pill` |
| Personchip | `PersonChip` | M3 filterchip + `PersonAvatar` |
| Valchip | `AppFilterChip` | M3 filterchip |
| Avatar | `PersonAvatar` | emoji i färgad cirkel |
| Antal ± | `QuantityStepper` | Expressive knappgrupp |
| Dosering | `DoseScheduleRow` | `AppFilterChip` + `QuantityStepper` |
| Mängdregel | `QuantityRuleRow` | `AppFilterChip` + `QuantityStepper` |
| Packprogress | `SuitcaseProgress` | Expressive vågig progress, `compact`-parameter |
| Laddning | `AppLoading` | Expressive laddningsindikator |
| Tomt tillstånd | `EmptyState` | ikon + text + `AppButton` |
| Bekräftelse | `ConfirmDialog` | M3 dialog, `destructive`-parameter |
| Bottom sheet | `AppBottomSheet` | M3 modal sheet |
| Meddelande | `AppSnackbarHost` | M3 snackbar |
| Firande | `Confetti` | egen `Canvas`, inget bibliotek |
| Avdelare | `AppDivider` | M3-avdelare med temats färg och tjocklek |
| Inställningsrad med växel | `SwitchRow` | `ItemRow` + M3-växel |
| Segmentval (t.ex. tema) | `AppSegmentedChoice` | M3 segmentknappar |
| Meny (arkivera/radera m.m.) | `AppMenu` | M3 rullgardinsmeny |
| Datumfält | `DateField` | `AppTextField` + M3 datumväljare |
| Horisontell rad av chips | `ChipRow` | horisontell lista av `PersonChip`/`AppFilterChip` |
| Förhandsvisning av mängd | `PackingPreview` | `ReasonFormatter` i `:core` |

## Beteende: interaktionsmönster → enda tillåtna ram

| Mönster | Ram | Beteende (identiskt överallt) |
|---|---|---|
| Listskärm | `EntityListScreen` | Laddning → `AppLoading`; tomt → `EmptyState` med primärknapp; fel → snackbar med "Försök igen"; lista av `ItemRow`; valfri gruppering (`SectionHeader`, `columns`); lägg till; "Visa arkiverade" där arkivering finns |
| Redigeraskärm | `EntityEditScreen` | Tillsammans med `EditorState<T>`: "Spara" aktiv först när giltig och ändrad (`saveLabel`-parameter); fältfel visas efter första försöket; bakåt med osparat → "Släng ändringar?"; sparfel → snackbar; lyckad sparning → tillbaka; arkivera/radera i menyn |
| Arkivera/dölj | `SwipeToHide` | Svep → dolt direkt → `UndoSnackbar` "%s arkiverad · Ångra" i 5 s |
| Ångra | `UndoSnackbar` | Enda ångra-mekanismen |
| Permanent radering | `ConfirmDialog` | `destructive = true`; alltid bekräftelse, aldrig svep |
| Förhandsvisning av beräkning | `PackingPreview` | Samma format i recept, kosttillskott, familjeartiklar |
| Komponentöversikt | `ComponentGallery` | Debug-skärm med alla komponenter och ramar i alla tillstånd |

Gemensamt för ramarna: felmeddelanden via `DataError.toMessage()`, skärmbyten via
`navigation/Transitions`, återkommande texter ("Spara", "Släng ändringar?", "Ångra",
"Försök igen", "Inget här än") en gång i `strings.xml`.

## Kod: delade byggstenar i andra lager

| Behov | Enda stället |
|---|---|
| Firestore-CRUD | `FirestoreCollection<T>(path, codec)` i `data/firestore/` |
| Sökvägar | `Paths` i `data/firestore/` (speglad i `tools/db/lib/collections.mjs`) |
| Map ↔ modell | `DocCodec<T>` + fälthjälpare i `:core` |
| Formulär | `EditorState<T>` + `Validator<T>` i `ui/common/` |
| Listtillstånd | `Flow<List<T>>.asListUiState()` → `ListUiState<T>` i `ui/common/` |
| Feltyp | `DataError` (`Offline`, `PermissionDenied`, `Cancelled`, `Unknown`) i `data/common/` |
| Fel till text | `DataError.toMessage()` i `ui/common/` |
| Arkivera | `Archivable` + `FirestoreCollection.setArchived` |
| Beräkningar | `PackingEngine` i `:core` |
| Mängd-, doserings- och regeltexter | `ReasonFormatter`, `DoseScheduleFormatter`, `QuantityRuleFormatter` i `:core` |
| Resultat av suspend-anrop | `suspendRunCatching` i `data/common/` (används av `FirestoreCollection` och `AuthRepository`) |
| Firestore-instans | `FirestoreModule` (Hilt) i `data/firestore/` |
| Inställningar | `SettingsStore` (DataStore) |
| Test-fakes och hjälpare | se skill `testing-strategy` |
| `tools/db`-logik | `tools/db/lib/` (admin, collections, walk, serialize, cli) |
| CI-steg | composite actions i `.github/actions/` |
| Byggkonfiguration | ett ställe (skill `android-gradle-logic`) |

## Förbjudet

Kontrolleras av `UiConsistencyTest` (Konsist) i bygget och av hooken `regel4-check`
direkt efter varje filändring. De exakta mönstren står **bara** i
`app/src/test/resources/ui-forbidden.txt` (enda källan, `mönster<TAB>ersättning`), och
undantag bara i `ui-allowlist.txt`. I korthet:

- **Feature-kod** (Kotlin i ett ui-paket utom `ui/components`, `ui/theme`, `ui/common` –
  även filer direkt under `ui/`):
  - Material 3-komponenter och layoutelement som har en motsvarighet i tabellerna ovan –
    t.ex. kort, knappar, kryss, växlar, fält, menyer, dialoger, sheets, avdelare, chips,
    progress, snackbar, `LazyColumn`/`LazyRow`, `Scaffold`, `SwipeToDismissBox`.
  - Hårdkodat utseende: färger, former, textstilar och literala dp för storlek och padding.
  - **Tillåtet:** `Text` med en `AppTypography`-stil, `Icon` (Material Symbols),
    `Column`/`Row`/`Box` och `Spacer` med `Spacing`.
- **All kod utom `data/firestore/`** – även `di/` och `ui/`: `FirebaseFirestore`,
  `CollectionReference`, `DocumentReference`. Därför ligger `FirestoreModule` i `data/firestore/`.
- **ViewModels** som heter `*EditViewModel` och inte exponerar en `EditorState`.
- **I `ui/components/`:** publik komponent utan skärmdump eller utan plats i `ComponentGallery`.
- **Undantag:** en rad i `ui-allowlist.txt` (`fil:symbol – motivering`). Filen är tom som
  standard. Varje ny rad kräver användarens uttryckliga ok i PR:en.

## Dokumentdrift

`UiConsistencyTest` läser komponenttabellen och ramtabellen i denna fil och jämför med
de publika `@Composable`-funktionerna i `ui.components`. Jämförelsen **aktiveras i #20**,
när katalogen byggs. Innan dess (inga publika composables i `ui.components`) hoppar testet
över den och skriver det i testloggen. Från #20:
- komponent i koden men inte i tabellen → bygget faller ("lägg till raden i shared-ui-components");
- komponent i tabellen men inte i koden → bygget faller ("ta bort raden eller bygg komponenten").

En ändring av denna fil kör därför app-testerna i CI (skill `ci-budget`).

## `cpdCheck` (copy-paste-detektor)

PMD CPD för Kotlin över `core/src`, `app/src/main` och `app/src/test`, tröskel ca 80
tokens (satt i #11). Fäller bygget. En träff betyder: bryt ut till en av byggstenarna
ovan eller utöka en ram. Tröskeln höjs inte för att "det bara är lite likt".

## Arbetsflöde

1. Slå upp elementtypen, mönstret eller behovet i tabellerna. Finns det → använd det.
2. Räcker det inte → **utöka** med en parameter vars default bevarar nuvarande utseende
   och beteende. Uppdatera bara de skärmdumpar som avsiktligt ändras.
3. Något nytt behövs → ny delad del i rätt paket, rad i tabellen, test, skärmdump, plats i
   `ComponentGallery` – och visad i mockup först (skill `mockup`).
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
