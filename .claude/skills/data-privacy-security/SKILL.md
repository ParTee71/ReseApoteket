---
name: data-privacy-security
description: ReseApotekets integritets- och säkerhetsregler — appen lagrar recept och mediciner per person, vilket är känslig hälsodata (GDPR art. 9). Ladda denna ALLTID när du rör loggning, Firestore, security rules, backup/export, tools/db, behörigheter, autentisering, hemligheter (nycklar/tokens/service accounts), release/minify eller något som exponerar eller lagrar användardata. Trigger-ord: integritet, säkerhet, privacy, GDPR, känslig data, hälsodata, recept, PII, logga, Log, secret, hemlighet, service account, FIREBASE_SERVICE_ACCOUNT, BACKUP_PASSPHRASE, keystore, jks, google-services, token, Firestore, firestore.rules, export, behörighet, permission, minify, R8, ProGuard.
---

# Datasäkerhet & integritet

ReseApoteket lagrar **känslig hälsodata**: vilka mediciner och kosttillskott varje person i
hushållet tar, i vilken dos. Det är persondata av särskild kategori (GDPR art. 9).
Behandla all sådan data som hemlig som standard.

## Regler

### 1. Logga aldrig hälsoinnehåll eller PII
Inga `Log.d/i/w/e`, `println` eller `console.log` med medicinnamn, doser, anteckningar,
personnamn, e-post, uid eller exporterat innehåll. Logga som mest undantagsklass,
ogenomträngliga ID:n eller antal. Ta bort felsökningsloggar innan du anser dig klar.
Ingen analytics eller crash-rapportering som skickar innehåll. Samma gäller `tools/db` och
GitHub Actions-loggar.

### 2. All data i Firestore ligger bakom security rules
- All användardata ligger under `households/{hid}/…`. `firestore.rules` släpper bara in
  inloggade användare vars uid finns i hushållets `memberUids`.
- Ny samling = ny rules-rad **och** rules-test i samma ändring (skill
  `firestore-data-layer`). En samling utan rules är ett säkerhetshål eller oåtkomlig.
- Rules deployas manuellt och medvetet, aldrig automatiskt från en PR.
- Appen **kräver inloggning**; ingen data läses eller skrivs utan `request.auth`.

### 3. Backup och export är krypterade och medvetna
- Veckobackupen (`tools/db export` i GitHub Actions) krypteras med gpg (AES-256, med
  integritetskontroll) och `BACKUP_PASSPHRASE`
  innan den laddas upp som artifact; klartextfilen raderas i samma jobb. Ladda aldrig
  upp okrypterad data som artifact, i en issue eller i en PR.
- Appens "Exportera som JSON" sker bara på uttrycklig användarhandling via dela-intent.
- Inga andra exportvägar, ingen extern lagring, inga `READ/WRITE_EXTERNAL_STORAGE`.

- Dekrypterade backuper och exporter läggs bara i `tools/db/` (git-ignorerad) och raderas
  efter användning. `.gitignore` fångar dessutom `backup*.json` och `export*.json` överallt.
- **Tester rör aldrig den riktiga databasen** – bara Firebase-emulatorn med projekt-ID
  `demo-reseapoteket` (skill `data-safety-backup`).

### 4. Hemligheter får aldrig checkas in eller klistras in
- Git-ignorerade och aldrig i repot: `google-services.json`, `*.jks`/`*.keystore`,
  `local.properties`, service-account-JSON, `tools/db/*.json`-exporter, `*.gpg`.
- I CI kommer de från GitHub-secrets (`GOOGLE_SERVICES_JSON`, `FIREBASE_SERVICE_ACCOUNT`,
  `BACKUP_PASSPHRASE`, `SIGNING_*`).
- I Claude-sessionen kommer `FIREBASE_SERVICE_ACCOUNT` från miljöns inställningar.
  **Be aldrig användaren klistra in en nyckel, token eller lösenfras i chatten**, och
  skriv aldrig ut värdet – kontrollera bara om variabeln är satt.
- Service account för Claude-miljön har **läsroll** (Cloud Datastore Viewer). En
  skrivande nyckel används bara i backup/import och bara när användaren uttryckligen bett
  om det (skill `db-access`).
- Signeringslösenord läses från `local.properties` eller miljövariabler, aldrig hårdkodat.

### 5. Visa bara det som frågats
När en Claude-session läser databasen (`tools/db`, agenten `db-inspektor`) visas bara de
fält och dokument frågan gäller – inga fulla dumpar i chatten.

### 6. Release ska minifieras
`isMinifyEnabled = true` + `isShrinkResources = true` på release. Bibliotek som kräver
keep-regler (kotlinx.serialization, Firestore-modeller, Hilt) får regler i
`app/proguard-rules.pro`. Minifierat bygge verifieras i veckoflödet `quality.yml` och i
release – inte per PR (skill `ci-budget`).

### 7. Behörigheter och autentisering
- Enda behörighet i dag: `INTERNET`. Nya behörigheter bara vid tydligt behov
  (t.ex. `POST_NOTIFICATIONS` när notifieringar kommer) och begärs först när de behövs.
- Inloggning går via `AuthRepository` (Credential Manager + Firebase) – anropa aldrig
  Firebase direkt från UI. Avbruten inloggning är inte ett fel.

## Checklista

- [ ] Inga loggar med hälsoinnehåll/PII – i appen, i `tools/db` och i CI.
- [ ] Ny samling har rules och rules-test.
- [ ] Backup/export är krypterad eller användarinitierad; inga nya exportvägar.
- [ ] Inga hemligheter incheckade, utskrivna eller efterfrågade i chatten.
- [ ] Nya bibliotek har keep-regler så release-minify inte går sönder.
- [ ] Inga nya/bredare behörigheter utan tydligt behov.
