---
name: db-access
description: Läsa ReseApotekets Firestore-databas från en Claude-session (telefon/webb) med tools/db. Ladda denna när användaren frågar om faktisk data, vill se vad som ligger i databasen, felsöka data, köra export/import/migrering eller när FIREBASE_SERVICE_ACCOUNT nämns. Trigger-ord: databasen, vad finns i, visa data, hur många, hushållet, personer i databasen, recept i databasen, Firestore-data, query, fråga databasen, tools/db, stats, export, import, migrera, service account, FIREBASE_SERVICE_ACCOUNT.
---

# Databasåtkomst från sessionen

Firestore nås med Node-verktygen i `tools/db/` (firebase-admin). För frågor om data –
använd agenten **`db-inspektor`**, så stannar dokumentinnehållet i agenten och bara svaret
kommer till huvudsessionen.

## Förutsättningar

- Miljövariabeln `FIREBASE_SERVICE_ACCOUNT` (base64 av service-account-JSON, rollen
  **Cloud Datastore Viewer**) i Claude-miljöns inställningar. Kontrollera bara **om** den
  finns (`[ -n "$FIREBASE_SERVICE_ACCOUNT" ] && echo satt || echo saknas`) – skriv aldrig
  ut värdet.
- Nätverk till `firestore.googleapis.com`, `oauth2.googleapis.com` och `registry.npmjs.org`.
- `tools/db/node_modules` – installeras av session-start-hooken (`npm ci`).

**Saknas variabeln:** förklara att användaren lägger in den under miljöns inställningar
(miljömenyn i sessionens titelrad → Edit → miljövariabler) och att en ny session krävs.
**Be aldrig användaren klistra in nyckeln i chatten.**

## Kommandon (läsning – förhandsgodkända i `settings.json`)

```bash
node tools/db/stats.mjs                                   # samlingar, antal dokument, schemaVersion
node tools/db/query.mjs households                        # hushåll kontot ser
node tools/db/query.mjs persons --fields name,emoji       # relativt aktivt hushåll
node tools/db/query.mjs persons/<pid>/regimens --where kind=MEDICINE --limit 20
node tools/db/query.mjs trips --json                      # rå JSON
node tools/db/get.mjs households/<hid>/trips/<tid>        # ett dokument
```

Alla har `--help`. Finns bara ett hushåll väljs det automatiskt; annars `--household <hid>`.

## Skrivande kommandon (kräver uttrycklig begäran och bekräftelse)

```bash
node tools/db/import.mjs --in backup.json --dry-run       # visa vad som skulle skrivas
node tools/db/import.mjs --in backup.json                 # skriv
node tools/db/migrate.mjs --to <N>                        # schemamigrering
```

- Kör **alltid** `--dry-run` först och visa sammanfattningen.
- Skrivning kräver en nyckel med skrivroll (`FIREBASE_SERVICE_ACCOUNT_RW`) – den ska bara
  finnas i miljön när användaren uttryckligen bett om det.
- `import` vägrar om filens `schemaVersion` är högre än appens.
- Aldrig ad hoc-skrivningar med egna skript eller `node -e`.

## Integritet (skill `data-privacy-security`)

- Visa bara de fält och dokument frågan gäller – inga fulla dumpar i chatten.
- Spara aldrig utdrag på disk utanför `tools/db/` och aldrig i repot.
- Exportfiler (`tools/db/*.json`) är git-ignorerade; radera dem när de inte behövs.

## Återställning från veckobackup

1. Ladda ner senaste artifact från körningen av `backup.yml` i GitHub Actions.
2. `openssl enc -d -aes-256-cbc -pbkdf2 -in backup.json.enc -out backup.json` (lösenfras
   från användarens lösenordshanterare – be inte om den i chatten; användaren kör steget).
3. `node tools/db/import.mjs --in backup.json --dry-run`, visa, och kör sedan utan `--dry-run`
   efter bekräftelse.
