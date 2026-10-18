# Claude-uppsättningen i ReseApoteket

Översikt över agenter och skills i `.claude/`. Reglerna står i [CLAUDE.md](../CLAUDE.md);
den här filen läses vid behov, så att CLAUDE.md – som laddas vid varje uppgift – hålls kort.
Dokumentkontrollen (`.github/scripts/check-links.mjs`) kräver att tabellerna listar exakt det
som finns i `.claude/agents/` och `.claude/skills/`.

## Agenter (`.claude/agents/`)

| Agent | När |
|---|---|
| `granskare` | Före varje PR: granskar diffen mot de fem reglerna och mockup-regeln, rapporterar i en tabell. |
| `ci-doktor` | Vid rött CI: läser loggar, skärmdumpsdiffar och CPD-rapporter, föreslår fix. |
| `testskrivare` | Skriver/utökar tester med de delade testhjälparna – bara när tre eller fler testnivåer berörs. |
| `db-inspektor` | Svarar på frågor om faktisk data i Firestore, endast läsning. |

## Modellval

Modellen väljs efter uppgiftens komplexitet – för sessionen och för varje agent. **Opus är
standard.** Frontmatterns modell är agentens standard; huvudsessionen anger `model` när
agenten startas, efter uppgiften. Sessionen byter själv via `/model`, eller låter en billigare
agent ta rutindelarna.

| Uppgift | Modell |
|---|---|
| Uppslag och läsning – loggar, data, sökningar | Haiku |
| Små avgränsade ändringar – en fil, en komponent, ett test | Sonnet |
| Flera lager, datamodell, migrering, backup och granskning | Opus |
| Väldigt komplexa issues – anges då i issuet | Fable |

## Skills (`.claude/skills/`)

| Skill | När |
|---|---|
| `android-projekt` | ReseApotekets delar av pluginens `android:`-skills (se nedan) – ladda alltid tillsammans med dem; mockupmallen och mockupens data och skärmdumpar; avsnitten Arbetsflöde, Issues och Release till `android:implement-issue`, `android:refine-issue` och `android:release`. |
| `data-privacy-security` | Hälsodata, loggning, hemligheter, minify. |
| **`data-safety-backup`** | Regel 1 – codecs, schemaversion, export/import och rundtur. |
| **`testing-strategy`** | Regel 2 – testnivåer, kontraktstester, skärmdumpar. |
| **`requirements-kravlista`** | Regel 3 – hålla KRAVLISTA.md aktuell. |
| **`shared-ui-components`** | Regel 4 – komponenter, ramar, byggstenar, förbud, kontroller. |
| **`ci-budget`** | Regel 5 – vad som körs när i CI. |
| `ui-style` | Designspråket (Material 3 Expressive); API:er i `ARKITEKTUR.md` → "Verifierade API:er". |
| `firestore-data-layer` | `FirestoreCollection`, `DocCodec`, repositories, offline, rules. |
| `packing-engine` | Beräkningsformler och kantfall. |
| `db-access` | Läsa databasen från en Claude-session. |

Fjorton generella skills kommer från pluginen `android@claude-android` och heter `android:<namn>`
i sessionen: `android-dev` (baslinje, ladda alltid), `compose-expert`, `kotlin-coroutines`,
`kotlin-flows`, `navigation-3`, `accessibility-compose`, `android-gradle-logic`, `firebase-auth`,
`mockup` (mockup före ny GUI), `refine-issue` (förfina en idé till ett planerat issue),
`implement-issue` (genomföra ett issue hela vägen till PR, med merge-kontroll först), `release`
(endast vid uttrycklig release; versionshöjningen i funktions-PR:en) samt Googles `android-intent-security` (manifest, Intents, delning)
och `firebase-security-rules-auditor` (vid varje ändring i `firestore.rules`, före FUT-2).
Pluginen är generell; ReseApotekets delar och nuvarande avvikelser står i `android-projekt`, och
där de krockar gäller projektets regler. Namnen listas i `.github/scripts/plugin-skills.txt`, som
dokumentkontrollen läser i stället för pluginen.

## Hooks (`.claude/hooks/`) och inställningar

Hur hookarna, behörigheterna och SDK-skriptet fungerar står i README, avsnittet
[Utveckling via Claude](../README.md#utveckling-via-claude).

## Gemensam plugin

`.claude/settings.json` slår på pluginen `android@claude-android` från det privata repot
[ParTee71/claude-android](https://github.com/ParTee71/claude-android) (`extraKnownMarketplaces`
+ `enabledPlugins`), som bär de generella Android-skillsen ovan. Lokalt (Android Studio, CLI)
installeras den därifrån. Molnsessioner får den via miljöns setup-skript, som klonar repot till
`/home/user/claude-android`. Repospecifikt hör aldrig hemma i pluginen – det står i `android-projekt`.
