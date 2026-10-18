# ReseApoteket – projektets grundregler

> Packförslag för mediciner, kosttillskott och apoteksvaror inför resa.
> Android/Kotlin · Compose (Material 3 Expressive) · Navigation 3 · MVVM · Hilt · Firestore · Firebase Auth.
> Kravspecifikation: [KRAVLISTA.md](KRAVLISTA.md) · Arkitektur: [ARKITEKTUR.md](ARKITEKTUR.md) (ADR-001, väg C)

Den här filen laddas automatiskt vid **varje** uppgift och gäller alltid, för alla
ändringar, oavsett storlek. De detaljerade reglerna ligger som skills i
`.claude/skills/` – den här filen är kontraktet som binder ihop dem.

**Svarslängd:** håll chattsvar minimala. Inga sammanfattningar, ingen upprepning av vad
som gjordes, inga rubriker/listor om inte nödvändigt. Kod, commits och PR-beskrivningar
skrivs normalt.

---

## De fem icke-förhandlingsbara reglerna

Vid **varje** ändring ska du, innan du anser arbetet klart, gå igenom alla fem.

### 1. Datasäkerhet – export/import får aldrig tappa data
All data i Firestore ska överleva en `tools/db export → import`-rundtur, och appen ska
tolerera okända fält och äldre `schemaVersion`. Nytt fält eller ny samling → default i
`:core`-modellen, tolerant `DocCodec`, `firestore.rules`, samlingslistan i
`tools/db/lib/collections.mjs` och rundturstest – i samma ändring.
→ Skill: **data-safety-backup**. Krav: DAT-, BCK-serierna.

### 2. Tester på alla nivåer
Ingen beteendeändring utan tester på rätt nivå: `:core` (ren JUnit), ViewModel
(Fake-repositories + Turbine), Compose via Robolectric i JVM, Roborazzi-skärmdumpar,
rules- och rundturstester mot Firebase-emulatorn. Befintliga tester uppdateras, tas
aldrig bort för att "bli gröna". **Flaky tester är buggar** – hitta grundorsaken och gör
testet deterministiskt, kör aldrig bara om.
→ Skill: **testing-strategy**. Krav: NFR-serien.

### 3. Kraven hålls aktuella
Ändrar du synligt beteende uppdateras [KRAVLISTA.md](KRAVLISTA.md) i samma PR – nytt krav
får nästa lediga ID, ändrat behåller sitt ID, borttaget stryks med `~~…~~ *(borttaget)*`.
Gemensamma beteenden beskrivs en gång (NFR/NAV) och refereras. README följer med när
funktionsomfånget ändras; `versionName` höjs i funktions-PR:en (se PR-flöde); själva releasen görs via skill **android:release**.
→ Skill: **requirements-kravlista**.

### 4. Enhetligt och utan dubbelkod
**Samma typ av element ser likadant ut och beter sig likadant överallt, och varje sak
finns på ett ställe.**

- **Utseende:** varje elementtyp (knapp, kort, listrad, kryssrad, textfält, rubrik,
  dialog, bottom sheet, pill, progress, tomt tillstånd, avatar, stepper …) har **exakt en**
  komponent i `ui/components/`. Feature-kod anropar aldrig Material 3-komponenter direkt
  och hårdkodar aldrig utseende (`Color(…)`, `RoundedCornerShape(…)`, `fontSize`,
  `TextStyle`, literala dp för hörn/höjd). Färg, form, typografi och avstånd kommer från
  `ui/theme` (`AppTypography`, `AppShapes`, `Spacing`, färgschemat).
- **Beteende:** varje interaktionsmönster finns **en gång**: listskärm (laddning, tomt,
  fel, lista, lägg till) = `EntityListScreen`; redigering (validering, spara aktiveras
  först när giltigt, "Släng ändringar?" vid bakåt, fel som snackbar) = `EntityEditScreen`
  + `EditorState`; arkivera/dölj = `SwipeToHide` + `UndoSnackbar`; permanent radering =
  alltid `ConfirmDialog`; felmeddelanden = `DataError.toMessage()`; skärmbyten =
  `navigation/Transitions`.
- **Kod i alla lager:** ingen kopierad logik. Firestore via generisk
  `FirestoreCollection<T>` + `DocCodec<T>`; listtillstånd via `asListUiState()`;
  beräkningar och texter om mängder i `:core` (`PackingEngine`, `ReasonFormatter`).
  Två ställen som gör samma sak → bryt ut *innan* det tredje dyker upp. Komposition
  (hjälpklasser, extensions, parametrar) före djupa basklasser. Gäller även tester,
  byggfiler, CI-flöden och `tools/db`.
- **Behövs en variant?** Utöka den delade delen med en parameter vars default bevarar
  nuvarande utseende och beteende. Aldrig en lokal kopia. Ny elementtyp/nytt mönster →
  ny delad del med test, skärmdump och plats i `ComponentGallery`.
- **Kontrolleras automatiskt:** hooken `regel4-check` ger besked direkt efter varje
  filändring; `UiConsistencyTest` (Konsist) och `cpdCheck` (copy-paste-detektor) fäller
  bygget. Förbjudna mönster finns i en fil (`ui-forbidden.txt`), undantag bara via
  `ui-allowlist.txt` med motivering och användarens ok i PR:en.
- **Gäller även dokumentationen:** varje regeltabell står i en skill; andra filer länkar.

→ Skills: **shared-ui-components**, **ui-style**, **firestore-data-layer**.

### 5. CI-budget
En PR får inte kosta mer än ~8 Actions-minuter, plus instrumenttesterna på Android-emulator
när PR:en rör koden de testar (användarens beslut) – release kör dem inte igen för samma kod.
Inget lint eller release-bygge i PR-flödet utan uttryckligt beslut; de körs på begäran,
veckovis och vid release. Nya beroenden eller moduler får inte fördubbla byggtiden – ange effekten i PR:en.

**Tester körs bara när koden de testar har ändrats.** Ett snabbt jobb avgör utifrån ändrade
sökvägar vilka testjobb som körs; Gradles build cache hoppar dessutom över oförändrade
testtasks. På `master` körs allt utom instrumenttesterna, och dokumentkontrollerna (skill-tabeller mot kod
och workflow, länkar, hooktest) körs alltid. Att slippa köra ett test är aldrig ett skäl att
inte skriva det.
→ Skill: **ci-budget** – enda källan för vad som körs när; kontrolleras mot `android.yml`.

## Processregel: mockup före ny GUI (skill **android:mockup**)

Utvecklingen sker mest från telefonen, och en release för att "se hur det blev" är dyr.

- Ny skärm, ny delad komponent eller tydligt ändrat utseende/beteende → visa först en
  **mockup** för användaren (Design-canvas-artifact, jfr B/C-jämförelsen
  https://claude.ai/artifact/6P25x1LRrkPBcF9BFqzi7p) och vänta på ok innan koden skrivs.
- Mockupen startar från mallen med komponent- och beteendearket och byggs av
  **katalogens komponenter och mönster** (regel 4), i appens designspråk, med riktiga svenska texter och realistisk data. Öppen fråga → två
  varianter sida vid sida. Något som saknas i katalogen märks "NY KOMPONENT".
- Länken läggs i issuet under rubriken **Design**.
- I PR:en visas resultatet med **Roborazzi-skärmdumpar** (ljust + mörkt) bredvid mockupen.
- Tumregel: syns förändringen i en tumnagel → mockup först. Padding, ordval eller
  ikonbyte → skärmdumpsdiffen i PR:en räcker.

→ Skills: **android-projekt** (mallens länk, data, skärmdumpar), **ui-style**, **android:refine-issue**,
**android:implement-issue** och pluginens **android:mockup**.

## Innan du skriver ny kod

1. **Sök efter befintligt mönster.** Komponent, ram, `FirestoreCollection`,
   `EditorState`, formatterare, use case eller testhjälpare – finns det, använd det.
   `Grep` i `ui/components/`, `ui/common/`, `data/common/`, `data/firestore/` och `core/` först.
2. **Följ arkitekturen** även om en genväg vore enklare: MVVM, `StateFlow<UiState>`,
   sealed events, repository som single source of truth, Hilt-DI, fel mappas i datalagret.
3. **Svenska** i allt användarvänt (UI-strängar i `strings.xml`, återkommande texter en
   gång); kod och identifierare på engelska; commit-meddelanden får vara svenska.
4. **Integritet:** recept är hälsodata. Logga aldrig innehåll eller PII, checka aldrig
   in nycklar (`google-services.json`, `*.jks`, service accounts). → skill
   **data-privacy-security**.

## Innan du anser dig klar (slutkontroll)

- [ ] **Datasäkerhet:** nya/ändrade fält i modell, codec, rules, samlingslista och rundturstest. (1)
- [ ] **Tester:** tillagda/uppdaterade på alla berörda nivåer; kontraktstester för skärmar som använder ramarna. (2)
- [ ] **Krav:** KRAVLISTA.md (och ev. README/version) speglar ändringen. (3)
- [ ] **Enhetligt:** inga råa M3-komponenter, egna interaktionsmönster, hårdkodade stilvärden eller kopierad logik; `UiConsistencyTest` och `cpdCheck` gröna. (4)
- [ ] **CI-budget:** inga nya dyra steg i PR-flödet; byggtidseffekt angiven vid nya beroenden. (5)
- [ ] **GUI:** mockup godkänd och skärmdumpar bifogade i PR:en.
- [ ] **Granskning:** agenten `granskare` och `/code-review` har körts på diffen – och igen på rättningarna efter dem (skill **android:implement-issue**, steg 8).

## PR-flöde

- **En PR i taget.** Öppna inte nästa förrän den föregående är mergad (eller uttryckligen
  stängd/pausad av användaren). Gäller `android:implement-issue` och manuellt arbete.
- **Kontrollera mergen via GitHub** (`pull_request_read`) innan nästa issue påbörjas –
  inte via antagande. Först när den är mergad startas arbetsbranchen om från `origin/master`.
- PR:er går mot `master`. Kör testerna lokalt först (skill **testing-strategy**), pusha sedan –
  GitHub Actions bekräftar.
- **Förbered releasen i funktions-PR:en.** Varje PR som ändrar appens beteende höjer
  versionen i samma ändring, så att ingen separat release-PR behövs. Välj semver efter
  ändringen: **major** = brytande/borttagen funktion eller backupformat som äldre appar inte
  läser, **minor** = ny funktion, **patch** = fix, chore, refactor. Höj `versionCode` med 1,
  uppdatera `version.properties` och versionshistoriken i README, och skriv versionen i PR-beskrivningen. Ren dokument-,
  test- eller CI-ändring utan effekt i appen höjer inte. Är versionen redan höjd sedan senaste
  tagg, höj bara om denna PR är allvarligare. Själva releasen (tagg, bygge, publicering) körs
  fortfarande bara på uttrycklig begäran (skill **android:release**) och kräver då ingen ny PR.
- **Prenumerera automatiskt** på PR-aktivitet (`subscribe_pr_activity`) så fort en PR
  skapas, utan att fråga. Bevaka tills den är mergad eller stängd: fixa CI-fel, hantera
  granskningskommentarer, schemalägg check-in. Vid rött CI: låt agenten `ci-doktor`
  diagnostisera. Avsluta prenumerationen när PR:en är klar.
- **Modell efter uppgift** för sessionen och varje agent, Opus som standard
  ([.claude/README.md](.claude/README.md) → Modellval).

## Bygg & test

```bash
./gradlew :core:test                    # domän, codecs, motor (sekunder, ingen Android SDK)
./gradlew :app:testDebugUnitTest        # ViewModel, Robolectric-UI, UiConsistencyTest
./gradlew :app:verifyRoborazziDebug     # jämför skärmdumpar mot referenser
./gradlew :app:recordRoborazziDebug     # spela in referenser (bara vid avsiktlig designändring)
./gradlew cpdCheck                      # copy-paste-detektor
./gradlew spotlessApply                 # kodstil (ktlint + compose-rules), före push – inte i CI
./gradlew :app:compileDebugKotlin       # kompilera
node tools/db/stats.mjs                 # databasöversikt (kräver FIREBASE_SERVICE_ACCOUNT)
node tools/db/query.mjs persons         # läs en samling
```

Kör det du ändrat lokalt innan varje push – även i fjärr-/telefonsessioner (skill
**testing-strategy**, "Innan du anser dig klar"). Kommandona gäller även i Android Studio.

## Arkitektur i korthet

```
Compose → ViewModel (StateFlow<UiState>) → Repository → FirestoreCollection<T> → Firestore (offline-cache)
                                                         ↑ DocCodec<T> i :core
```

`:core` är ren Kotlin/JVM (modeller, codecs, `PackingEngine`); `:app` är Android-appen.
Utseende och gemensamt beteende finns bara i `ui/theme`, `ui/components` och `ui/common`;
Firestore bara i `data/firestore`; felmappning en gång i `data/common`. Paketträd,
dataflöde och datamodell står i [ARKITEKTUR.md](ARKITEKTUR.md) (enda källan).

## Databasåtkomst från sessionen

Läs med `tools/db` (skill **db-access**, agent **db-inspektor**). Kräver miljövariabeln
`FIREBASE_SERVICE_ACCOUNT` i Claude-miljöns inställningar – be aldrig användaren klistra
in en nyckel i chatten. Skrivningar bara på uttrycklig begäran via `import.mjs`/`migrate.mjs`.

## Agenter och skills

Agenterna (`granskare`, `ci-doktor`, `testskrivare`, `db-inspektor`) och alla skills, med
när de används, listas i [.claude/README.md](.claude/README.md).

**Kortrapport:** agenternas svar hamnar i huvudsessionens kontext och kostar där. `granskare` och
`ci-doktor` svarar bara med mallen i sin fil – inga referat av det de läst, ingen upprepning av
uppdraget, högst ~15 rader om inte fynden kräver mer.

## Lång session

Vid komprimering av sammanhanget: behåll listan över ändrade filer, issue- och PR-nummer,
testkommandon som körts med resultat, och öppna ❌ från granskningen.

> Reglerna gäller både i Claude-appen på telefonen och i Claude i Android Studio – båda
> läser denna fil och `.claude/`.
