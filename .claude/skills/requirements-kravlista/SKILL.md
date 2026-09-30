---
name: requirements-kravlista
description: ReseApotekets kravregel (regel 3) — KRAVLISTA.md ska alltid spegla appens faktiska beteende. Ladda denna ALLTID när du lägger till, ändrar eller tar bort synligt beteende, en funktion, en skärm, en inställning, en beräkningsformel, ett UI-flöde eller en regel för data/backup/CI. Trigger-ord: krav, kravlista, KRAVLISTA, specifikation, requirement, feature, funktion, beteende, formel, beräkning, versionsbump, ändra UI, ta bort funktion, NFR.
---

# Hålla kraven aktuella (KRAVLISTA.md)

**Regel:** varje ändring av användarsynligt beteende – och av icke-funktionella regler
som tester, CI och backup – speglas i [KRAVLISTA.md](../../../KRAVLISTA.md) i **samma**
PR. Kraven är projektets sanning om vad appen ska göra; kod och krav får aldrig glida isär.

## Format och konventioner

KRAVLISTA.md består av numrerade avsnitt med tabeller `| ID | Krav |`. Varje krav har ett
**stabilt ID** med prefix per område:

| Prefix | Område |
|---|---|
| `ÖV` | Översikt och syfte |
| `TP` | Teknisk plattform |
| `NAV` | Navigation |
| `AUTH` | Konto och inloggning |
| `HUS` | Hushåll |
| `PER` | Personer |
| `REC` | Recept (mediciner) |
| `KOS` | Kosttillskott |
| `FAM` | Familjeartiklar |
| `RES` | Resa |
| `BER` | Beräkning (packförslag) |
| `PAK` | Packlista |
| `SET` | Inställningar |
| `DAT` | Datamodell |
| `BCK` | Backup och export |
| `NFR` | Icke-funktionella krav |
| `FUT` | Framtida (planerat, ej byggt) |

| Du gör | Så här |
|---|---|
| **Nytt beteende** | Ny rad i rätt tabell med **nästa lediga ID** i serien (t.ex. `PER-4`). Återanvänd aldrig ett ID. |
| **Ändrat beteende** | Redigera texten på befintligt ID. Behåll ID:t. |
| **Borttaget beteende** | Behåll raden, stryk: `~~Visa arkiverade personer i …~~ *(borttaget)*`. |
| **Planerat men ej byggt** | Markera `*(planerad)*` sist i raden; ta bort markeringen i PR:en som bygger det. |
| **Helt nytt område** | Nytt numrerat avsnitt och nytt prefix – lägg även prefixet i tabellen ovan. |

## Gemensamma beteenden beskrivs en gång

Beteenden som ramarna ger alla skärmar (listskärmens tillstånd, redigering och
"Släng ändringar?", arkivera med ångra, radering med bekräftelse, felmeddelanden,
synkindikator) står som **NFR-/NAV-krav en gång**. Funktionskraven refererar till dem
(`Personlistan följer NFR-1 och NFR-3.`) i stället för att upprepa texten – regel 4
gäller även kravlistan.

## Formler är krav

BER-kraven innehåller beräkningsformlerna **ordagrant** som `PackingEngine` implementerar
dem (skill `packing-engine`). Ändras en formel ändras BER-raden i samma PR, och testet som
bevisar den.

## Spårbarhet

- Varje krav ska kunna bevisas av ett test (regel 2). Skriv krav-ID i testnamnet eller en
  kommentar när det inte är uppenbart (`// BER-3`).
- Krav om persisterad data (DAT, BCK) hänger ihop med skill `data-safety-backup`.
- Krav om CI och tester (NFR) hänger ihop med skills `ci-budget` och `testing-strategy`.

## Följdartefakter

- **README.md** – funktionslistan och versionstabellen när en funktion tillkommer eller försvinner.
- **Version** – `versionName`/`versionCode` höjs **bara vid release**, aldrig i en
  funktions-PR. Releasen görs bara på uttrycklig begäran (skill `release`).

## Checklista

- [ ] Rätt tabell och ID-serie.
- [ ] Nytt ID / redigerat / struket med `~~…~~ *(borttaget)*`; `*(planerad)*` borttaget när det byggts.
- [ ] Inga ID:n återanvända eller raderade.
- [ ] Gemensamma beteenden refererade, inte upprepade.
- [ ] README uppdaterad om funktionsomfånget ändrats (version höjs vid release).
- [ ] Ett test bevisar det nya/ändrade kravet.
