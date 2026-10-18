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
| `android-dev` | Baslinje för all Android/Kotlin-utveckling (ladda alltid). |
| `compose-expert` · `kotlin-coroutines` · `kotlin-flows` | Compose-, coroutine- och flow-detaljer. |
| `android-gradle-logic` | Gradle, version catalog, convention plugins. |
| `firebase-auth` | Google-inloggning (Credential Manager + Firebase). |
| `accessibility-compose` | TalkBack, tryckytor, dynamisk text. |
| `data-privacy-security` | Hälsodata, loggning, hemligheter, minify. |
| **`data-safety-backup`** | Regel 1 – codecs, schemaversion, export/import och rundtur. |
| **`testing-strategy`** | Regel 2 – testnivåer, kontraktstester, skärmdumpar. |
| **`requirements-kravlista`** | Regel 3 – hålla KRAVLISTA.md aktuell. |
| **`shared-ui-components`** | Regel 4 – komponenter, ramar, byggstenar, förbud, kontroller. |
| **`ci-budget`** | Regel 5 – vad som körs när i CI. |
| `ui-style` | Designspråket (Material 3 Expressive); API:er i `ARKITEKTUR.md` → "Verifierade API:er". |
| `mockup` | Ny skärm, komponent eller ändrat utseende: mockup före kod. |
| `firestore-data-layer` | `FirestoreCollection`, `DocCodec`, repositories, offline, rules. |
| `packing-engine` | Beräkningsformler och kantfall. |
| `db-access` | Läsa databasen från en Claude-session. |
| `refine-issue` | Förfina en idé till ett planerat issue (med Design och Återbruk). |
| `implement-issue` | Genomföra ett issue hela vägen till PR enligt reglerna (med merge-kontroll först). |
| `release` | Endast vid uttrycklig release. |
| `firebase-security-rules-auditor` | *Googles (Firebase).* Granska `firestore.rules` – vid varje rules-ändring, före FUT-2. |
| `android-intent-security` | *Googles (Android).* Granska exporterade komponenter, notisers och delningens Intents. |
| `navigation-3` | *Googles (Android).* Referens för Navigation 3: djuplänkar, flera stackar, scener. |

Googles skills är oförändrade kopior (källa, commit och licens i deras `SOURCE.md`); där de
krockar med projektets regler gäller projektets.

## Hooks (`.claude/hooks/`) och inställningar

Hur hookarna, behörigheterna och SDK-skriptet fungerar står i README, avsnittet
[Utveckling via Claude](../README.md#utveckling-via-claude).
