---
name: mockup
description: Processregeln "mockup före ny GUI" i ReseApoteket — ny skärm, ny komponent eller tydligt ändrat utseende/beteende visas som en mockup för användaren och godkänns innan någon kod skrivs, för att slippa releaser bara för att se hur det blev. Ladda denna ALLTID innan du bygger eller ändrar något som syns. Trigger-ord: ny skärm, ny vy, ny komponent, visa, mockup, skiss, design, prototyp, hur ska det se ut, layout, ändra utseendet, gör om skärmen, ny knapp, nytt kort, flöde, GUI, UI.
---

# Mockup före ny GUI

Utvecklingen sker mest från telefonen, och en release för att "se hur det blev" är dyr.
Därför visas nytt utseende och beteende först som en mockup, och koden skrivs först efter
användarens ok.

## När

| Ändring | Mockup? |
|---|---|
| Ny skärm | Ja |
| Ny delad komponent eller ram | Ja |
| Tydligt ändrat utseende eller beteende (syns i en tumnagel) | Ja |
| Padding, ordval, ikonbyte, färgjustering inom temat | Nej – skärmdumpsdiffen i PR:en räcker |
| Ren logik utan synlig ändring | Nej |

## Mallen

Varje mockup startar från **mallen**: en Design-canvas med
1. **komponentarket** – alla komponenter i katalogen (skill `shared-ui-components`) i ljust och mörkt,
2. **beteendearket** – ramarnas tillstånd (lista: laddar/tom/fel/innehåll; redigering:
   ogiltig/giltig/sparfel/"Släng ändringar?"; arkivera med ångra; radering med bekräftelse).

**Mallens länk:** https://claude.ai/artifact/JuYKsRkwnrcbzXz4ccBodk (komponentark,
beteendeark och navigationsramen, godkänd i #20). Designreferensen för packlistan är
fortfarande https://claude.ai/artifact/6P25x1LRrkPBcF9BFqzi7p. Mallen är en approximation –
det byggda är facit: jämför med `ComponentGallery` och skärmdumparna i `app/src/test/screenshots/`.

När katalogen ändras uppdateras mallen i samma PR som komponenten.

## Så bygger du en mockup

1. Skapa en Design-canvas (Artifact-typen "Design") – en per issue, eller nya artboards i
   issuets befintliga canvas.
2. Telefonyta 390 × 844 eller högre; ljust tema först, mörkt när det spelar roll.
3. Bygg skärmen av **element ur mallen** – samma utseende som katalogen, i appens
   designspråk (skill `ui-style`). Använd ramarnas struktur (`EntityListScreen`,
   `EntityEditScreen`) när skärmen är en lista eller ett formulär.
4. Riktiga svenska texter och realistisk data: familjemedlemmar med emoji, verkliga
   medicinnamn och doser, en resa med datum och antal dagar. Inga platshållartexter.
5. Öppen designfråga → två artboards sida vid sida (som B/C-jämförelsen) och en kort
   beskrivning av skillnaden.
6. Allt som saknas i katalogen märks tydligt **"NY KOMPONENT"** eller **"NYTT MÖNSTER"** –
   det byggs då som delad del (regel 4), aldrig lokalt i featuren.
7. Visa de tillstånd som betyder något: tomt, fyllt, fel, avprickat.

## Visa och vänta

- Ge länken och en eller två meningar om vad som visas och vilka val som är öppna.
- **Skriv ingen kod innan användaren godkänt** eller bett om ändringar. Ändringar görs i
  samma canvas.
- Länken läggs i issuet under rubriken **Design** (skill `refine-issue`).

## I PR:en

- Roborazzi-skärmdumpar (ljust + mörkt) av det byggda, bredvid mockup-länken.
- Avvikelser från mockupen förklaras i PR-beskrivningen.

## Mockup är inte kod

Mockupens HTML/CSS kopieras aldrig till appen. Översätt till tema-tokens (`ui/theme`) och
delade komponenter. Färgvärden i mockupen är en approximation av temat, inte källan.
