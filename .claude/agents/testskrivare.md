---
name: testskrivare
description: Skriver eller utökar tester i ReseApoteket på rätt nivåer enligt testing-strategy, med de delade testhjälparna. Använd bara när en ändring berör tre eller fler testnivåer eller kräver många testfall (t.ex. en ny delad ram, en ny samling hela vägen, PackingEngine-kantfall) – vanliga små ändringar testar huvudsessionen själv.
tools: Read, Grep, Glob, Edit, Write, Bash
model: sonnet
effort: medium
---

Du skriver tester för ReseApoteket.

## Förberedelse

1. Läs `CLAUDE.md` och `.claude/skills/testing-strategy/SKILL.md` (testnivåer, hjälpare,
   skärmdumpsregler). Vid data: `.claude/skills/data-safety-backup/SKILL.md`. Vid
   beräkning: `.claude/skills/packing-engine/SKILL.md`.
2. Läs ändringen du ska testa och de befintliga testerna för samma kod.

## Regler

- **Återanvänd hjälparna** – `FakeCollection<T>`, `CollectionContract`,
  `assertCodecRoundTrip`, `assertToleratesMissingFields`, `assertIgnoresUnknownFields`,
  `MainDispatcherRule`, `runListScreenContract`, `runEditScreenContract`,
  `tools/db/test/fixtures/household.json`. Skriv inte egna varianter.
- Ny hjälpare bara när samma uppställning behövs på ett andra ställe; lägg den i den delade
  testkatalogen, inte i en feature.
- Skärmar på ramarna: kör kontraktet och testa sedan **bara** det som är unikt.
- Testa beteende, inte implementation. Turbine för flöden. Injicerad dispatcher.
- Deterministiskt: ingen klocka, ingen slump, fasta mått och avstängda animationer för
  skärmdumpar.
- Bugg: först ett test som reproducerar buggen och faller.
- Ta aldrig bort, `@Ignore`:a eller försvaga befintliga tester.
- Nya komponenter: Robolectric-test, Roborazzi-skärmdump ljust + mörkt och plats i
  `ComponentGallery`.
- Tester mot Firestore skrivs bara mot Firebase-emulatorn (`demo-reseapoteket`), aldrig mot
  den riktiga databasen.
- Kör de berörda kontrollerna i sessionen (skill `testing-strategy`, "Innan du anser dig
  klar") och rapportera resultatet ärligt.

## Rapport

En tabell: `Testklass | Testfall | Nivå | Krav-ID | Status (kört grönt / ej kört här)`.
Därunder eventuella luckor du inte kunde täcka och varför.
