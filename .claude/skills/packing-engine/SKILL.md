---
name: packing-engine
description: ReseApotekets beräkningsmotor — hur mycket av varje medicin, kosttillskott och familjeartikel som ska packas för en resa. Ladda denna ALLTID när du rör PackingEngine, ReasonFormatter, DoseScheduleFormatter, QuantityRuleFormatter, doseringar, mängdregler, marginaldagar, förhandsvisning av mängd, packförslag, omräkning eller BER-krav. Trigger-ord: beräkning, räkna, formel, packförslag, hur många, mängd, dos, dosering, dagligen, veckodagar, intervall, vid behov, marginal, marginaldagar, förpackning, kartor, avrundning, räkna om, merge, reason, förklaring.
---

# Beräkningsmotorn (`:core`)

`PackingEngine` räknar fram packförslaget. Den är **rena funktioner** i `:core`
(`se.partee71.reseapoteket.core.engine`): inga coroutines, ingen klocka, ingen Android,
ingen Firestore. Allt den behöver skickas in. Det gör den trivial att testa och gör att
samma formel kan användas i packlistan och i förhandsvisningarna.

Formlerna nedan är **krav** (BER-serien i KRAVLISTA.md). Ändras en formel ändras BER-raden
och testet i samma PR (skill `requirements-kravlista`).

## Modeller (i `core/.../model/`)

Tabellen visar det motorn använder. Fullständiga fält och lagring: `ARKITEKTUR.md` →
"Datamodell" (enda källan).

| Modell | Innehåll |
|---|---|
| `Regimen` | `kind` (`MEDICINE`/`SUPPLEMENT`), namn, form, styrka, `unit`, `packageSize?`, `schedule`, `active`, `travelDay`, `travelDayTime?`, `archived` |
| `DoseSchedule` | sealed: `Daily(dosesPerDay, unitsPerDose)`, `Weekdays(days: Set<DayOfWeek>, unitsPerDose)`, `IntervalDays(intervalDays, unitsPerDose)`, `AsNeeded(quantity)`, `Unknown` |
| `FamilyItem` | namn, kategori, `unit`, `rule`, `active`, `travelDay`, `travelDayTime?`, `archived` |
| `QuantityRule` | sealed: `Fixed(quantity)`, `PerTraveller(quantity)`, `PerDays(quantity, perDays)`, `Unknown` |
| `Trip` | `startDate: LocalDate?` (saknas → `Weekdays` ger varning), `days`, `travellerIds` |
| `HouseholdSettings` | `marginDays` (default 2) |
| `PackingItem` | sektion, `personId?`, `sourceType` (`REGIMEN`/`FAMILY_ITEM`/`MANUAL`), `sourceId?`, namn, `unit`, `suggestedQuantity`, `quantity`, `packages?`, `reason`, `checked`, `checkedAt?`, `hidden`, `travelDay`, `travelDayTime?` (följer källan vid ny lista och "Räkna om", PAK-6) |

## API

```kotlin
object PackingEngine {
    fun suggest(trip: Trip, persons: List<Person>, regimensByPerson: Map<String, List<Regimen>>,
                familyItems: List<FamilyItem>, settings: HouseholdSettings): PackingSuggestion  // items + warnings
    fun merge(existing: List<PackingItem>, suggestion: PackingSuggestion): List<PackingItem>
    fun regimenLine(regimen: Regimen, trip: Trip, settings: HouseholdSettings): PackingLine      // mängd, förpackningar, reason, warning
    fun familyLine(item: FamilyItem, travellers: Int, trip: Trip, settings: HouseholdSettings): PackingLine
    fun regimenQuantity(schedule: DoseSchedule, trip: Trip, settings: HouseholdSettings): Int?  // null = kan inte räknas ut
    fun familyQuantity(rule: QuantityRule, travellers: Int, trip: Trip, settings: HouseholdSettings): Int?
}
```

`regimenQuantity` och `familyQuantity` är **enda stället** där formlerna finns. `suggest`
och förhandsvisningarna (`PackingPreview` i recept, kosttillskott och familjeartiklar)
anropar `regimenLine`/`familyLine` – ingen formel skrivs två gånger (regel 4). Recepten
skickas per person (`regimensByPerson`, samma form som `RegimenRepository.regimensFor`),
eftersom `Regimen` inte bär sitt person-ID. Rad-ID:n är stabila (`regimen-<pid>-<rid>`,
`family-<fid>`).

## Formler

`effectiveDays = trip.days + settings.marginDays`

| Typ | Antal enheter |
|---|---|
| `Daily` | `dosesPerDay × unitsPerDose × effectiveDays` |
| `Weekdays` | antal datum `d` i `[startDate, startDate + effectiveDays)` där `d.dayOfWeek ∈ days`, × `unitsPerDose` |
| `IntervalDays` | `ceil(effectiveDays / intervalDays) × unitsPerDose` |
| `AsNeeded` | `quantity` |
| `Unknown` | 0 + varning ("kan inte beräknas – uppdatera appen") |
| `Fixed` | `quantity` |
| `PerTraveller` | `quantity × antal resenärer` |
| `PerDays` | `ceil(effectiveDays / perDays) × quantity` |

- Resultatet avrundas **uppåt** till helt antal enheter.
- `packages = ceil(quantity / packageSize)` när `packageSize` finns.
- Urval: bara `active && !archived` regimens för personer i `travellerIds` som själva
  inte är arkiverade; bara `active && !archived` familjeartiklar.
- Samma medicinnamn hos två personer ger **två rader** (en per person).
- Sortering: sektion (Mediciner, Kosttillskott, Övrigt) → person (`sortOrder`, via den
  generiska `Sortable`-jämföraren) → namn i **svensk ordning** (å, ä, ö sist); familje-
  artiklar på `sortOrder` → namn.
- Veckodagar utan startdatum kan inte räknas ut: 0 + varning ("startdatum saknas").

## Texter

| Formatterare | Exempel | Används i |
|---|---|---|
| `ReasonFormatter` | "2 × 1 tablett/dag × (7 + 2) dagar = 18 tabletter → 2 kartor à 10" | packlistans rad, `PackingPreview` |
| `DoseScheduleFormatter` | "1 tablett morgon och kväll", "1 kapsel mån, ons, fre", "1 dos var 3:e dag", "vid behov: ta med 10" | receptlistan, redigering, reason |
| `QuantityRuleFormatter` | "1 per resenär", "1 tub per 7 dagar" | familjeartiklar, reason |

Svensk pluralböjning och enhetsnamn finns **en gång** (i en `UnitNames`-tabell) och
används av alla tre formatterarna.

## `merge` – räkna om en befintlig packlista

För varje förslagsrad matchas befintlig rad på (`sourceType`, `sourceId`, `personId`):
- **Finns, mängden inte manuellt ändrad** (`quantity == suggestedQuantity`) → ny
  `suggestedQuantity` och `quantity`.
- **Finns, manuellt ändrad** → ny `suggestedQuantity`; `quantity` och radens `packages`
  behålls (förpackningarna hör till mängden – den som ändrar mängden räknar om dem).
- **`checked`, `checkedAt`, `hidden`** behålls alltid.
- **Ny i förslaget** → läggs till.
- **Saknas i förslaget** (t.ex. resenär borttagen) → tas bort om inte avprickad; avprickad
  rad behålls och får förklaringen "Inte längre i förslaget" (`NO_LONGER_SUGGESTED`).
- **`MANUAL`-rader** rörs aldrig.
- **Dubbletter** av samma nyckel: den första matchas, övriga behandlas som försvunna.
- Resultatet får entydig `sortOrder`: förslagets rader, sedan kvarvarande avprickade, sedan
  egna rader (i sin tidigare ordning).
- Texterna ("Inte längre i förslaget", "kan inte beräknas – uppdatera appen" m.fl.) finns
  en gång, i `Texts`.

## Skydd mot trasiga data

Värden kommer från Firestore och kan vara orimliga (äldre eller nyare app, BCK-5). Motorn
litar inte på dem:
- `days` och `marginDays` räknas som 0–`MAX_DAYS` (366) var; negativa antal som 0.
- Varje mängd räknas i `Long` och begränsas till `MAX_QUANTITY` (99 999) – inget överlopp.
- Intervall och `perDays` under 1 räknas som 1; `packageSize` 0 ger inga förpackningar.
- Sorteringen är deterministisk: `sortOrder`, sedan svenskt namn (och styrka), sedan ID – samma
  lista oavsett i vilken ordning data kommer.
- `weekdayCount` är den enda räkningen av veckodagar; förklaringen använder den i stället för
  att räkna baklänges ur mängden.
- `PackingSuggestion.warnings` innehåller läkemedelsnamn (hälsodata) – logga dem aldrig.

## Förhandsvisningens texter

`ReasonFormatter.amount(line, unit)` ("18 tabletter · 2 kartor"), `tripContext(days)`
("För en 7-dagarsresa") och `familyContext(travellers, days)` ("För 4 resenärer i 10 dagar")
är texterna i `PackingPreview` och packlistan – ingen skärm formulerar dem själv.

## Kantfall som ALLTID ska ha test (`core/src/test/.../engine/`)

- 0 dagar och 1 dag.
- Resa över årsskifte och över skottdag (`Weekdays`).
- `Weekdays` där inga dagar matchar → 0.
- `IntervalDays` med intervall 1.
- Person utan recept; arkiverad person som resenär (ignoreras).
- Samma medicinnamn hos två personer → två rader.
- `AsNeeded` utan mängd → 0 + varning; `Unknown` → 0 + varning.
- Inaktiv och arkiverad regimen/familjeartikel ignoreras.
- `packageSize` som inte går jämnt upp → avrundning uppåt.
- `merge` bevarar manuellt ändrad mängd, `checked`, `hidden` och `MANUAL`-rader.
- `ReasonFormatter`-text för varje schematyp och regel, `Weekdays` med `unitsPerDose` > 1.
- Orimliga värden (se *Skydd mot trasiga data*), lika `sortOrder`, `merge` två gånger.
- Varje `ItemUnit` har namn och förpackning.

## Anti-mönster

- En formel i en ViewModel eller composable (förhandsvisning ska anropa motorn).
- `LocalDate.now()` inne i motorn – datum skickas in.
- En egen mängdtext i en feature i stället för formatterarna.
- Avrundning nedåt eller till närmaste – alltid uppåt.
