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

## Funktioner

Kraven med ID:n står i [KRAVLISTA.md](KRAVLISTA.md); här är översikten.

**Finns i dag (0.1.0, skelett):**
- Inloggning med Google-konto; hushållet skapas automatiskt vid första inloggningen (AUTH, HUS).
- Data i Cloud Firestore som fungerar offline och synkas när nätet är tillbaka (ÖV-5).
- "Uppdatera appen" om hushållets data sparats av en nyare appversion (HUS-3).
- Veckovis krypterad backup och återställning med `tools/db` (BCK).
- Databasåtkomst från en Claude-session, bara läsning (TP-8).
- Fyra flikar – Resor, Personer, Familjeartiklar och Inställningar – i en flytande verktygsrad (NAV).
- Designsystemet: Material 3 Expressive med egen palett och Nunito, ljust, mörkt eller system (SET-1).
- Personer med recept och kosttillskott: dosering, förhandsvisning av vad som ska packas, pausade recept och snabbval (PER, REC, KOS).
- Komponentkatalogen och ramarna för listor och redigering; i debug-bygget visar Inställningar → Komponenter alla delar.

**Planerat:**
- Familjeartiklar på hushållsnivå, t.ex. solkräm och plåster (#24).
- Resor och packförslag som räknas ut från resans längd och vilka som åker med (#25).
- Packlista med avprickning (#26) och inställningar (#27).

## Kom igång (Android Studio)

1. Android Studio (senaste stabila) med JDK 17 och Android SDK-plattform 37 (`compileSdk`).
2. Klona repot och öppna det. Utan `app/google-services.json` bygger appen ändå (debug får
   en stubbad Firebase-konfiguration mot demo-projektet), men inloggning fungerar inte – se
   [Firebase-setup](#firebase-setup).
3. Byggkommandon och vilka tester som finns: [CLAUDE.md → Bygg & test](CLAUDE.md#bygg--test).
   Arkitektur och byggkonfiguration: [ARKITEKTUR.md](ARKITEKTUR.md).

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
1. Skapa en läsnyckel (rollen **Cloud Datastore Viewer**, Firebase-setup steg 3) och lägg den
   base64-kodad som miljövariabeln `FIREBASE_SERVICE_ACCOUNT` (tabellen ovan).
2. Tillåt nätverksvärdarna ovan och starta en **ny** session – hooken installerar `tools/db`
   och talar om att nyckeln är satt.
3. Fråga Claude ("hur många recept har Anna?") – agenten `db-inspektor` använder
   `tools/db` och visar bara det du frågat om. Själv kan du köra
   `node tools/db/stats.mjs`, `query.mjs <samling>` och `get.mjs <sökväg>` (alla har `--help`).

Verktygen läser bara och sparar inget på disk. Se skill
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
   - `reseapoteket-backup` med rollen **Cloud Datastore Viewer** (backupen läser bara) →
     nyckeln base64-kodad som GitHub-secret `FIREBASE_SERVICE_ACCOUNT` (veckobackupen).
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

## Backup och återställning

`backup.yml` exporterar all data varje måndag 03:00 UTC (och på begäran: Actions → Backup →
Run workflow), krypterar den med gpg och sparar `backup.json.gpg` som artifact i 90 dagar.

**Inställning (en gång):** skapa en lång lösenfras och lägg den som GitHub-secret
`BACKUP_PASSPHRASE` **och** i din lösenordshanterare – utan den går backupen inte att öppna.
`FIREBASE_SERVICE_ACCOUNT` enligt Firebase-setup ovan.

**Återställning** (från din dator eller en Claude-session med skrivnyckel):
1. Ladda ner artifact `backup-<nr>` från körningen och packa upp `backup.json.gpg`.
2. Dekryptera till den git-ignorerade katalogen `tools/db/` – lösenfrasen skriver du själv,
   aldrig i chatten:
   ```bash
   gpg --decrypt --output tools/db/backup.json backup.json.gpg
   ```
   gpg kontrollerar integriteten: en trasig eller ändrad fil ger fel i stället för fel data.
3. Kontrollera och skriv:
   ```bash
   node tools/db/import.mjs --in tools/db/backup.json --dry-run
   node tools/db/import.mjs --in tools/db/backup.json
   ```
   Importen kontrollerar hela filen innan något skrivs och vägrar hushåll med nyare
   `schemaVersion` än verktyget. Ett enda hushåll: `--household <hid>`. Utan `--replace` ligger
   dokument som tillkommit efter backupen kvar; med `--replace` blir hushållet exakt som i
   backupen (torrkörningen visar vad som skulle tas bort).
   Kör när appen inte används: importen skriver över dokument som ändrats under tiden.
4. Radera `tools/db/backup.json`.

---

## Versionshistorik

Versionen höjs bara vid release (skill [`release`](.claude/skills/release/SKILL.md)).

| Version | Innehåll |
|---|---|
| 0.1.0 | Skelett: byggsystem, CI, datamodell och datalager (#11–#15), inloggning och hushåll (#16), backup (#17), databasåtkomst från sessionen (#18). Ingen release publicerad ännu. |
