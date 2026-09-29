---
name: accessibility-compose
description: Tillgänglighet (a11y) i ReseApotekets Compose-UI — appen ska fungera med TalkBack, stor text och tillräckliga tryckytor när familjen packar inför en resa. Ladda denna när du bygger eller ändrar UI: ikoner, knappar, klickbara ytor, bilder, kryssrader, kort, fält, steppers, progress. Trigger-ord: tillgänglighet, accessibility, a11y, TalkBack, contentDescription, semantics, stateDescription, touch target, tryckyta, skärmläsare, dynamisk text, fontScale, kontrast, mergeDescendants.
---

# Tillgänglighet i Compose

ReseApoteket ska kunna användas av alla i familjen – även med skärmläsare, förstorad text
och nedsatt motorik. Tillgänglighet är ett kvalitetskrav, inte en extrafunktion.

Eftersom varje elementtyp har **en** delad komponent (regel 4, skill
`shared-ui-components`) löses tillgänglighet **i komponenten**, en gång, och gäller då
överallt. Fixa aldrig semantik per anropare – utöka den delade komponenten.

## Regler

### 1. contentDescription — meningsbärande vs dekorativt
- **Meningsbärande** ikon/bild (`AppIconButton`, statusikon, `PersonAvatar`) ska ha en
  beskrivande, **svensk** `contentDescription` från `strings.xml`.
- **Rent dekorativ** grafik (sektionsikon bredvid rubriktext, `Confetti`) ska ha
  `contentDescription = null` så att TalkBack hoppar över den.
- Upprepa inte synlig text i `contentDescription` på samma nod (dubbelläsning).

### 2. Tryckytor minst 48dp
Alla klickbara element ska ha minst **48×48dp** tryckyta. Små ikoner använder
`AppIconButton` eller `Modifier.minimumInteractiveComponentSize()` – aldrig en naken
24dp-ikon med `clickable`.

### 3. Stöd dynamisk textstorlek
Text och `lineHeight` i **`sp`** via `AppTypography`. Lås inte höjder så att förstorad
text klipps. `ComponentGallery` har en Roborazzi-skärmdump med `fontScale = 1.3` som
fångar klippning.

### 4. Gruppera och semantik
- En rad som är **en** logisk enhet (`ItemRow`, `CheckRow`) använder
  `Modifier.semantics(mergeDescendants = true)` så TalkBack läser namn, mängd och status
  i ett svep.
- `CheckRow`: `Role.Checkbox` + `stateDescription` ("packad"/"inte packad"); annonsera
  namn och mängd, t.ex. "Enalapril 10 mg, 18 tabletter, inte packad".
- `QuantityStepper`: knapparna har "Minska"/"Öka" + aktuellt värde och enhet.
- `SuitcaseProgress`: `contentDescription` "9 av 14 packat".
- `UndoSnackbar`: ångra-åtgärden ska vara nåbar och annonseras.

### 5. Färg är aldrig ensam bärare av information
Sektionsfärger (Medicin/Kosttillskott/Övrigt) och personfärger kompletteras alltid med
text eller ikon. Kontrast minst 4.5:1 för brödtext (dämpad text på vit yta ≥ `#6B5A50`)
i både ljust och mörkt tema.

## Tester (regel 2)

Compose-UI-test i JVM (Robolectric) kan assertera semantik:
```kotlin
composeTestRule.onNodeWithContentDescription("Tillbaka").assertHasClickAction()
composeTestRule.onNode(hasStateDescription("inte packad")).assertIsDisplayed()
```
Lägg sådana assertions i den delade komponentens test när du ändrar en interaktiv
komponent. Nya delade komponenter verifieras för `contentDescription`, `Role` och
tryckyta.

## Checklista

- [ ] Meningsbärande ikoner/bilder har svensk `contentDescription`; dekorativa har `null`.
- [ ] Klickbara element ≥ 48dp tryckyta.
- [ ] Text i `sp` via `AppTypography`; galleriets `fontScale = 1.3`-skärmdump utan klippning.
- [ ] Logiska grupper merge:ade; tillstånd har `stateDescription`/`Role`.
- [ ] Ingen information enbart via färg; kontrast ≥ 4.5:1 i ljust och mörkt tema.
- [ ] Semantiken ligger i den delade komponenten, inte hos anroparen.
