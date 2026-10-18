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

**Finns i dag (0.4.1):**
- Inloggning med Google-konto; hushållet skapas automatiskt vid första inloggningen (AUTH, HUS).
- Data i Cloud Firestore som fungerar offline och synkas när nätet är tillbaka (ÖV-5).
- "Uppdatera appen" om hushållets data sparats av en nyare appversion (HUS-3).
- Veckovis krypterad backup och återställning med `tools/db` (BCK).
- Databasåtkomst från en Claude-session, bara läsning (TP-8).
- Fyra flikar – Resor, Personer, Familjeartiklar och Inställningar – i en flytande verktygsrad (NAV).
- Designsystemet: Material 3 Expressive med egen palett och Nunito, ljust, mörkt eller system (SET-1).
- Personer med recept och kosttillskott: dosering, förhandsvisning av vad som ska packas, pausade recept och snabbval (PER, REC, KOS).
- Nytt recept föreslår läkemedel ur Läkemedelsverkets lista medan man skriver och fyller i namn, styrka, form och enhet – listan är inbyggd och sökningen sker på telefonen (REC-6–7).
- Familjeartiklar på hushållsnivå, t.ex. solkräm och plåster, med mängdregler och exempel att börja från (FAM).
- Resor: skapa en resa som räknar fram packlistan från resans längd och vilka som åker med, och räkna om när resan ändras (RES).
- Packlistan: pricka av per sektion och person, ändra mängd, lägg till egna rader – en i taget eller en hel inklistrad lista under Övrigt (PAK-7) – dölj med svep och ångra, och konfetti när allt är packat (PAK).
- Inställningar: hushållets namn och marginaldagar, "Flytta avprickade sist", tema, konto med utloggning, och export av hushållet som JSON i samma format som backupen (SET).
- Påminnelser inför resan: ingen packlista än, dags att packa, dagen före och resedagens morgon – för de personer telefonen valt, med egna tider (PÅM).
- Packas på resedagen: det som används till sista morgonen samlas sist i packlistan och påminns om på resedagens morgon eller vid en egen tid (PAK-6).
- Komponentkatalogen och ramarna för listor och redigering; i debug-bygget visar Inställningar → Komponenter alla delar.

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
- `CLAUDE.md`, skills och agenter i `.claude/` (översikt i [`.claude/README.md`](.claude/README.md)).
- `.claude/settings.json` förhandsgodkänner utan fråga:
  - läsning av PR:er, CI-status och jobbloggar, och bevakning av PR-aktivitet;
  - schemalagda check-ins och triggers (skapa, ändra, ta bort, köra) – samma val som i
    Dagboken, så att Claude kan följa upp PR:er utan att du behöver svara;
  - läsande databaskommandon (`tools/db` query, get, stats);
  - hooktestet och dokumentkontrollerna (`node --test` på de två testkatalogerna, kört
    från repots rot med relativa sökvägar – i en session med flera repon frågar Claude först).
  Skrivande databaskommandon (`import`, `migrate`) kräver alltid bekräftelse.
- Samma fil hindrar Claude från att öppna nyckelfiler med läsverktyget (`google-services.json`, keystores,
  service accounts, `.env`) och sätter `LC_ALL=C.UTF-8`, så att svenska tecken i testnamn och
  skärmdumpar blir rätt i molnsessionen.
- Hooken `session-start.sh` installerar `tools/db` (utan livscykelskript) och talar om ifall
  databasnyckeln finns.
- Hooken `regel4-check.mjs` kontrollerar regel 4 direkt efter varje filändring, med samma
  mönster som bygget (`app/src/test/resources/ui-forbidden.txt`).
- Samma hook körs som Stop-hook innan Claude avslutar en tur: den går igenom alla filer som
  ändrats mot `origin/master` och ber Claude rätta en träff först (högst en gång i följd).
- `install-android-sdk.sh` installerar Android SDK i `/opt/android-sdk` och pekar ut den i
  `local.properties`, så att Gradle-testerna kan köras i en molnsession. Det körs inte
  automatiskt (cirka 450 MB); lägg det i miljöns setup-skript eller be Claude köra det.
- Hookarna hittar repot både när det är sessionens projektrot och när sessionen har flera
  repon under en gemensam katalog; Stop-hooken kontrollerar då varje repo med dess egen hook.

### Det du ställer in i Claude-miljön (en gång)
Görs i Claude-appen: miljömenyn i sessionens titelrad → **Edit**. Nya inställningar gäller
från nästa session.

**Miljövariabler**

| Variabel | Innehåll | När |
|---|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | service-account-JSON (rå eller base64) med rollen **Cloud Datastore Viewer** (läs) | för att läsa databasen från sessionen |
| `FIREBASE_SERVICE_ACCOUNT_RW` | samma, men rollen **Cloud Datastore User** | bara när du uttryckligen vill att Claude ska kunna importera/migrera |
| `FIREBASE_PROJECT_ID` | projekt-ID som nyckeln måste höra till | bara om det inte är `reseapoteket` |

Nyckeln skapas i Google Cloud Console → IAM → Service accounts → Keys. Filens innehåll kan
läggas in som det är eller base64-kodat (`base64 -w0 nyckel.json`). **Klistra aldrig in en nyckel i chatten**, och checka aldrig in
den – den hör bara hemma i miljöns inställningar och i GitHub-secrets.

**Nätverk** – tillåt dessa värdar:
`firestore.googleapis.com`, `oauth2.googleapis.com`, `www.googleapis.com`,
`registry.npmjs.org`, `dl.google.com`, `maven.google.com`, `services.gradle.org`,
`plugins.gradle.org`, `repo.maven.apache.org`.

### Läsa databasen från sessionen
1. Skapa en läsnyckel (rollen **Cloud Datastore Viewer**, Firebase-setup steg 3) och lägg den
   som miljövariabeln `FIREBASE_SERVICE_ACCOUNT` (tabellen ovan).
2. Tillåt nätverksvärdarna ovan och starta en **ny** session – hooken installerar `tools/db`
   och talar om att nyckeln är satt.
3. Fråga Claude ("hur många recept har Anna?") – agenten `db-inspektor` använder
   `tools/db` och visar bara det du frågat om. Själv kan du köra
   `node tools/db/stats.mjs`, `query.mjs <samling>` och `get.mjs <sökväg>` (alla har `--help`).

Verktygen läser bara och sparar inget på disk. Se skill
[`db-access`](.claude/skills/db-access/SKILL.md).

### Tester och CI
Claude kör testerna lokalt i sessionen före push; GitHub Actions bekräftar när en PR öppnas.
Bara det som berörs av ändringen körs; vad
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
   **Authentication → Google** och **Firestore** i production mode, region `europe-north2` (Stockholm).
2. **Android-app:** lägg till appen med paketet `se.partee71.reseapoteket` och SHA-1 för både
   debug- och release-nyckeln. Ladda ner `google-services.json` till `app/` (git-ignorerad) och
   lägg innehållet som GitHub-secret `GOOGLE_SERVICES_JSON` (release-bygget skriver filen).
   Utan filen bygger appen ändå, men inloggning fungerar inte.
3. **Service accounts** (Google Cloud Console → IAM → Service accounts):
   - `reseapoteket-backup` med rollen **Cloud Datastore Viewer** (backupen läser bara) →
     nyckeln som GitHub-secret `FIREBASE_SERVICE_ACCOUNT` (veckobackupen).
   - `reseapoteket-rules` med rollerna **Firebase Rules Admin** och **Service Usage Consumer**
     (får bara ändra security rules, inte läsa data) → nyckeln som GitHub-secret
     `FIREBASE_RULES_DEPLOYER`.
   - en läsnyckel med rollen **Cloud Datastore Viewer** → miljövariabeln
     `FIREBASE_SERVICE_ACCOUNT` i Claude-miljön (se ovan).

   Nyckelfilens innehåll läggs in som det är (eller base64-kodat). `tools/db` och deploysteget
   stoppar en nyckel som hör till ett annat projekt än `reseapoteket`.
4. **Security rules:** `firestore.rules` deployas av GitHub Actions, alltid efter rules-testerna
   mot emulatorn och aldrig från en PR:
   - **Actions → Rules → Run workflow** (`rules.yml`): provkör (`--dry-run`) som
     standard; kryssa i **deploy** för att deploya på riktigt (bara från `master`).
   - **Release** (`release.yml`) deployar rules före publiceringen när de ändrats sedan förra
     releasen, så att appen aldrig släpps före sina rules.

   Reservväg, och för `firestore.indexes.json` (deploynyckeln får inte ändra index): från din
   egen dator, inloggad med `npx --prefix tools/db firebase login`:
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

Versionen höjs i funktions-PR:en; releasen görs via skill [`release`](.claude/skills/release/SKILL.md)).

| Version | Innehåll |
|---|---|
| 0.1.0 | Skelett: byggsystem, CI, datamodell och datalager (#11–#15), inloggning och hushåll (#16), backup (#17), databasåtkomst från sessionen (#18). Aldrig publicerad. |
| 0.2.0 | Första releasen. Expressive-tema och navigering (#20); personer (#21), recept med dosering (#22), kosttillskott (#23) och familjeartiklar med mängdregler (#24); resor med packförslag (#25) och packlista med avprickning (#26); inställningar, konto och export (#27). Stabilare hushållssession, navigering och rules (#50–#52); rules deployas från Actions (#64). |
| 0.3.0 | Egen appikon – resväska med apotekskors, även som enfärgad temaikon (#70); textfälten börjar med stor bokstav (#70); APK:n heter `ReseApoteket-<version>-<tidsstämpel>.apk` (#69). |
| 0.4.0 | Påminnelser inför resan – ingen packlista än, dags att packa, dagen före och resedagens morgon, per telefon och person med egna tider (#78, PÅM); "Packas på resedagen" med egen sektion i packlistan och påminnelse på morgonen eller vid egen tid (#79, PAK-6); synkindikator när ändringar väntar eller nätet saknas (#75, NFR-6); nytt recept föreslår läkemedel ur Läkemedelsverkets lista och fyller i namn, styrka, form och enhet (#82, REC-6–7). |
| 0.4.1 | Säkerhetshärdning: storleksgränser och föräldrakontroll i databasreglerna, och textfälten med samma tak (#87); bara påminnelsens egen länk öppnar en resa, en resa som inte finns visar "Det finns inte längre", och en delad export rensas ur telefonen efter en timme (#88). |
| 0.4.2 | Buggfixar: "Skapa packlista" ger packlistan med recepten i stället för åtkomstfel och en tom lista (#95); resedagens notis visar antal (#93); uppdaterad läkemedelslista. |
| 0.5.0 | Klistra in en lista i packlistan: "Lägg till" → Lista lägger in en sak per rad under Övrigt, med antal, förhandsvisning och Ångra (#97, PAK-7); uppdaterad läkemedelslista. |
| 0.5.1 | Buggfix: en lång inklistrad lista går att lägga till – bladet scrollar, fältet visar högst 8 rader och knappen står direkt under fältet (#100). |
| 0.5.2 | Packlistan på telefon: Övrigt visas i en kolumn så att namnen får plats, och två kolumner bara där de ryms; ett långtryck på en kapad rad visar hela texten (#103, NFR-20); uppdaterad läkemedelslista. |
