---
name: ui-style
description: ReseApotekets designspråk (väg C, Material 3 Expressive) — enkelt, stilfullt, lite kul och tydligt. Ladda denna när du bygger eller ändrar tema, färger, typografi, former, rörelse, texter, ikoner, tomma tillstånd eller en komponents utseende, och tillsammans med skill mockup. Trigger-ord: design, stil, utseende, färg, palett, tema, mörkt läge, typsnitt, font, Nunito, rundad, hörn, animation, rörelse, fjädrande, Expressive, ikon, emoji, text, formulering, tomt tillstånd, konfetti, lekfull, snyggt.
---

# Designspråket

**Känsla:** enkelt, stilfullt, lite kul och tydligt. Familjen ska snabbt se vad som är
packat, och det ska kännas lite belönande att pricka av.

**Referens:** den godkända mockupen av packlistan (väg C, höger artboard):
https://claude.ai/artifact/6P25x1LRrkPBcF9BFqzi7p

**Var saker står (enda källor):**
- Vilken komponent eller ram som används för vad → skill `shared-ui-components`.
- Exakta Expressive- och Navigation 3-API:er och versioner → `ARKITEKTUR.md` →
  "Verifierade API:er" (#11). **Gissa aldrig ett API-namn.**
- Tokens i kod → `ui/theme` (`AppColors`, `AppTypography`, `AppShapes`, `Spacing`).

## Tema

- `MaterialExpressiveTheme` med `MotionScheme.expressive()`. **Ingen dynamic color** – appen
  har en egen identitet.
- Ljust, mörkt och system (inställning i appen).

| Token | Ljust | Användning |
|---|---|---|
| Primär | korall `#BF4A2A` (4,6:1 mot ytan, 5,0:1 mot vit) | primärknappar, kryss, progress, sektionsrubrik Mediciner |
| Yta | aprikos `#FFF4EE` | bakgrund |
| Kort | vit | `ApotekCard` |
| Text | `#241B16` | brödtext |
| Dämpad text | `#6B5A50` (≥ 4.5:1 mot vit) | undertext, avprickade rader |
| Sektion Mediciner | korall | `SectionHeader`, ikonruta |
| Sektion Kosttillskott | grön `#5A7328` | |
| Sektion Övrigt | blå `#2C6584` | |
| Personfärger | åtta förval via `AppColors.person(index)` | avatar, vänsterkant på personens rader |

Mörkt tema härleds i `ui/theme` med samma roller; kontrast kontrolleras i båda. Varje
färg som bär text ska klara 4,5:1 mot sin bakgrund – räkna efter vid ny nyans.

## Typografi (`AppTypography`)

- **Nunito 800** för rubriker: resans namn som stor rundad display (~34 sp), sektionsrubriker.
- **Nunito Sans** för brödtext.
- Mängder och räknare stora och feta: "18 tabletter · 2 kartor", "9 av 14 packat".
- Namngivna stilar (`screenTitle`, `sectionTitle`, `itemTitle`, `itemSubtitle`, `body`,
  `quantity`, `pill`, `button`). Aldrig `fontSize` i feature-kod.

## Former och avstånd

- `AppShapes`: kort 28 dp, rader 18 dp, pills och knappar helt rundade, sheet 28 dp upptill,
  ikonrutan i ett tomt tillstånd 40 dp (`iconTile`).
- `Spacing`: `xs 4 · s 8 · m 12 · l 16 · xl 24 · xxl 32` dp – enda tillåtna avstånden.
- Tryckytor minst 48 dp.

## Rörelse

- Fjädrande (Expressive spring) på kryss, chips, split-knapp och progress.
- Kryss: formmorf till fylld cirkel, raden tonas till dämpad "packad"-stil.
- Skärmbyten: samma rörelse överallt, definierad en gång i `navigation/Transitions`.
- 100 % packat: kort `Confetti` + "Allt packat! 🧳 Trevlig resa".
- Rörelse förstärker, blockerar aldrig: inga väntetider för att se en animation.

## Beteende hör till stilen

Samma bekräftelser, samma ångra-snackbar, samma tomma tillstånd och samma felmeddelanden
överallt – de kommer från ramarna i `shared-ui-components`, aldrig från en enskild skärm.

## Texter

- Korta, vardagliga, svenska: "Vem åker med?", "Hur många dagar?", "Lägg till", "Allt packat!".
- Inga medicinska förkortningar utan förklaring ("tabl" → "tabletter").
- Återkommande texter finns en gång i `strings.xml` och används av ramarna.
- Tomma tillstånd: en mening om vad som saknas + en primärknapp som löser det.

## Ikoner och emoji

- **Emoji** i innehåll: personavatarer, firande, tomma tillstånd.
- **Material Symbols** i kontroller (knappar, navigation, sektionsikoner). Aldrig emoji som
  knappikon.

## Tillgänglighet

Enligt skill `accessibility-compose` – semantiken ligger i komponenterna.

## Skärmdumpar

Varje komponent har Roborazzi-referens i ljust och mörkt; en designändring uppdaterar
referenserna i samma PR (skill `testing-strategy`).

## Expressive-opt-in

Opt-in för experimentella Expressive-API:er görs bara i `ui/theme` och `ui/components`
(`@file:OptIn`), så att feature-koden inte sprider annotationer.
