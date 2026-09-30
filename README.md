# ReseApoteket – Android

Räknar ut hur mycket av familjens mediciner, kosttillskott och apoteksvaror som ska packas
inför en resa, och låter er pricka av det som plockats fram.

**Utvecklingsregler:** [CLAUDE.md](CLAUDE.md) · **Arkitektur:** [ARKITEKTUR.md](ARKITEKTUR.md) ·
**Plan:** [issues](https://github.com/ParTee71/ReseApoteket/issues)

> Projektet byggs i huvudsak med Claude Code, både från telefonen och i Android Studio.
> Läs [CLAUDE.md](CLAUDE.md) först – den samlar de fem icke-förhandlingsbara reglerna.
> Detaljer ligger som skills i [`.claude/skills/`](.claude/skills/) och agenter i
> [`.claude/agents/`](.claude/agents/).

---

## Utveckling via Claude

### Det som ligger i repot
- `CLAUDE.md`, skills och agenter i `.claude/`.
- `.claude/settings.json` förhandsgodkänner utan fråga:
  - läsning av PR:er, CI-status och jobbloggar, och bevakning av PR-aktivitet;
  - schemalagda check-ins och triggers (skapa, ändra, ta bort, köra) – samma val som i
    Dagboken, så att Claude kan följa upp PR:er utan att du behöver svara;
  - läsande databaskommandon (`tools/db` query, get, stats);
  - hooktestet och dokumentkontrollerna (`node --test` på de två testkatalogerna, kört
    från repots rot med relativa sökvägar – i en session med flera repon frågar Claude först).
  Skrivande databaskommandon (`import`, `migrate`) kräver alltid bekräftelse.
- Hooken `session-start.sh` installerar `tools/db` (utan livscykelskript) och talar om ifall
  databasnyckeln finns.
- Hooken `regel4-check.mjs` kontrollerar regel 4 direkt efter varje filändring, med samma
  mönster som bygget (`app/src/test/resources/ui-forbidden.txt`).
- Hookarna hittar repot både när det är sessionens projektrot och när sessionen har flera
  repon under en gemensam katalog.

### Det du ställer in i Claude-miljön (en gång)
Görs i Claude-appen: miljömenyn i sessionens titelrad → **Edit**. Nya inställningar gäller
från nästa session.

**Miljövariabler**

| Variabel | Innehåll | När |
|---|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | base64 av service-account-JSON med rollen **Cloud Datastore Viewer** (läs) | för att läsa databasen från sessionen |
| `FIREBASE_SERVICE_ACCOUNT_RW` | samma, men rollen **Cloud Datastore User** | bara när du uttryckligen vill att Claude ska kunna importera/migrera |
| `FIREBASE_PROJECT_ID` | projekt-ID | bara om det inte framgår av nyckeln |

Nyckeln skapas i Google Cloud Console → IAM → Service accounts → Keys. Koda den med
`base64 -w0 nyckel.json`. **Klistra aldrig in en nyckel i chatten**, och checka aldrig in
den – den hör bara hemma i miljöns inställningar och i GitHub-secrets.

**Nätverk** – tillåt dessa värdar:
`firestore.googleapis.com`, `oauth2.googleapis.com`, `www.googleapis.com`,
`registry.npmjs.org`, `dl.google.com`, `maven.google.com`, `services.gradle.org`,
`plugins.gradle.org`, `repo.maven.apache.org`.

### Läsa databasen från sessionen
Fråga Claude ("hur många recept har Anna?") – agenten `db-inspektor` använder
`tools/db` och visar bara det du frågat om. Se skill
[`db-access`](.claude/skills/db-access/SKILL.md).

### Tester och CI
Tester körs i GitHub Actions när en PR öppnas. Bara det som berörs av ändringen körs; vad
som körs när, och vilka flöden som bara körs på begäran, står i skill
[`ci-budget`](.claude/skills/ci-budget/SKILL.md).

**Grenskydd (en gång, i GitHub):** Settings → Branches → `master` → kräv statuskontrollen
`ci-ok`. Gör inte `gradle` eller `rules` obligatoriska – hoppade jobb rapporterar ingen status.

Dokumentkontrollerna och hooktestet kan köras lokalt:

```bash
node --test '.github/scripts/*.test.mjs'
node --test '.claude/hooks/test/*.test.mjs'
```

---

## Firebase-setup

Görs en gång av dig i Firebase- och GitHub-konsolerna. Inga nycklar hör hemma i repot eller i
chatten (skill [`data-privacy-security`](.claude/skills/data-privacy-security/SKILL.md)).

1. **Projekt:** skapa Firebase-projektet `reseapoteket` (Spark-planen räcker). Aktivera
   **Authentication → Google** och **Firestore** i production mode, region `europe-north1`.
2. **Android-app:** lägg till appen med paketet `se.partee71.reseapoteket` och SHA-1 för både
   debug- och release-nyckeln. Ladda ner `google-services.json` till `app/` (git-ignorerad) och
   lägg innehållet som GitHub-secret `GOOGLE_SERVICES_JSON` (release-bygget skriver filen).
   Utan filen bygger appen ändå, men inloggning fungerar inte.
3. **Service accounts** (Google Cloud Console → IAM → Service accounts):
   - `reseapoteket-tools` med rollen **Cloud Datastore User** → nyckeln base64-kodad som
     GitHub-secret `FIREBASE_SERVICE_ACCOUNT` (veckobackupen, #17).
   - en läsnyckel med rollen **Cloud Datastore Viewer** → miljövariabeln
     `FIREBASE_SERVICE_ACCOUNT` i Claude-miljön (se ovan).
4. **Security rules:** `firestore.rules` deployas manuellt och medvetet – aldrig från CI –
   från din egen dator, inloggad med `npx --prefix tools/db firebase login`:
   ```bash
   npm ci --prefix tools/db
   npx --prefix tools/db firebase deploy --only firestore:rules,firestore:indexes --project reseapoteket
   ```

**Testa reglerna lokalt** (kräver Java 21; startar Firebase-emulatorn med ett demo-projekt och rör
aldrig den riktiga databasen):
```bash
npm ci --prefix tools/db
npx --prefix tools/db firebase emulators:exec --only firestore --project demo-reseapoteket "npm --prefix tools/db test"
```

*Funktioner, byggkommandon, backup och versionshistorik fylls på i #19.*
