---
name: data-safety-backup
description: ReseApotekets datasäkerhetsregel (regel 1) — export/import och schemaändringar får ALDRIG tappa användardata. Ladda denna ALLTID när du lägger till, ändrar eller tar bort persisterad data: ett fält på en modell, en ny Firestore-samling, en DocCodec, schemaVersion, SchemaMigrator, firestore.rules, tools/db (export, import, migrate) eller backup.yml. Trigger-ord: backup, säkerhetskopia, restore, återställ, export, import, migrering, schemaversion, schemaVersion, DocCodec, codec, fält, ny samling, collection, Firestore, rundtur, round-trip, datamodell, tools/db, dataförlust.
---

# Datasäkerhet: codecs, schemaversion, export/import

**Invariant:** all data i Firestore överlever en **`tools/db export → import`-rundtur**
med identiskt innehåll, och appen läser både äldre och okända dokument utan att krascha
eller skriva sönder dem. En ändring som lägger till persisterad data utan att föra in den
i hela kedjan är en regression även om allt kompilerar – datan kan tappas vid nästa
återställning eller skrivas över av en äldre appversion.

Krav: DAT- och BCK-serierna i KRAVLISTA.md.

## Kedjan (var datan passerar)

```
:core-modell ⇄ DocCodec<T> ⇄ Map<String, Any?> ⇄ FirestoreCollection<T> ⇄ Firestore
                                                                            │
          tools/db import  ←  backup (gpg-krypterad i Actions)  ←  tools/db export
```

| Led | Fil | Ansvar |
|---|---|---|
| Modell | `core/.../model/` | `data class` med **default på alla icke-obligatoriska fält**; `Identified`, `Sortable`, `Archivable` där det passar |
| Codec | `core/.../schema/*Codec.kt` | `encode`/`decode` byggda **enbart** av de delade fälthjälparna (`map.string`, `map.int`, `map.enum`, `map.localDate`, `map.nested`, `map.stringList` …) |
| Schema | `core/.../schema/Schema.kt`, `SchemaMigrator.kt` | `Schema.CURRENT_VERSION`; migreringssteg per version |
| Åtkomst | `app/.../data/firestore/FirestoreCollection.kt`, `Paths.kt` | Enda stället som talar med Firestore; sökvägar på ett ställe |
| Regler | `firestore.rules` | Bara samlingarna i `collections.mjs`, var och en skyddad av `memberUids` och med typkontroll av kända fält; `schemaVersion` minst 1, kan inte sänkas och höjs högst till `maxSchemaVersion()` |
| Samlingslista | `tools/db/lib/collections.mjs` | Enda listan export/import/query går igenom; testad mot `Paths` och rules |
| Backup | `.github/workflows/backup.yml` | Veckovis export, krypterad artifact, 90 dagar |
| Appens export | `core/.../schema/ExportFormat.kt` + `app/.../data/firestore/HouseholdExporter.kt` | "Exportera som JSON" (SET-5): samma format som `tools/db export` (`serialize.mjs`, `backup.mjs`), läser dokumenten rått längs `Paths` – aldrig via codecarna, så att okända fält följer med |

## Schemaversion

- `households/{hid}.schemaVersion` anger dataformatet.
- **Högre än `Schema.CURRENT_VERSION`** → appen visar "Uppdatera appen" och skriver
  ingenting (en gammal app får aldrig skriva över nyare data).
- **Lägre** → `SchemaMigrator` lyfter dokumenten vid läsning; skrivning sker i nytt format.
  Versionen hör till hushållet, inte till varje dokument, så **varje steg är idempotent** och
  hushållets `schemaVersion` höjs av en fullständig migrering av alla dess dokument
  (`tools/db/migrate.mjs`, i release-ordningen i skill `release`) – aldrig av en vanlig skrivning.
  Ändrar inget steg dit några dokument (`SchemaMigrator.Step.UNCHANGED`, `canStamp`) stämplar
  appen själv hushållet när det öppnas (`HouseholdSession`), så att en äldre app genast ber om
  uppdatering. Ett steg som ändrar data har `changesData = true` och kräver `migrate.mjs`.
- Saknas `schemaVersion`, eller är den under `Schema.FIRST_VERSION` (trasigt värde), gäller
  `Schema.FIRST_VERSION` (`Schema.versionOf`, `versionOf` i `lib/schema.mjs`), aldrig "senaste".
- Höjs versionen: nytt steg i `SchemaMigrator` **och** spegling i `tools/db/migrate.mjs`,
  test per steg, och `lib/schema.mjs` samt `maxSchemaVersion()` i `firestore.rules` följer med
  (ett test jämför konstanterna). De nya rules deployas **före** appen (`rules.yml`, och
  `release.yml` före publiceringen) – annars nekas migreringens stämpling.
- De flesta ändringar kräver **ingen** versionshöjning: ett nytt valfritt fält med default
  läses redan av gamla dokument. Höj bara vid betydelseändring (omdöpt fält, ändrad enhet,
  ändrad struktur) **och vid ett nytt enum-värde eller en ny `schedule`/`rule`-variant** –
  en äldre app tolkar okända enum-värden som default och skulle skriva tillbaka fel värde.
- Okänd `schedule`/`rule`-typ blir `Unknown(raw)` och skrivs tillbaka oförändrad.
- Modellens defaults = vad ett tomt dokument betyder (därför är `schedule`/`rule` `Unknown`
  som default); `assertToleratesMissingFields` kontrollerar det.

## Skrivningar bevarar okända fält

Eftersom nya fält inte höjer `schemaVersion` kan en äldre appversion läsa ett dokument med
fält den inte känner till. Den får aldrig radera dem när den sparar:

- **`FirestoreCollection.upsert` skriver med `set(…, SetOptions.merge())`**, aldrig ett helt
  dokument. Fält som inte finns i codecen lämnas orörda i Firestore.
- **Codecens `encode` skriver alla fält den känner till – även de utan värde, som `null`.**
  Annars skulle ett fält som användaren tömt (t.ex. `packageSize`) ligga kvar vid merge.
  `decode` behandlar `null` och saknat fält likadant (default).
- **Nästlade objekt** (`schedule`, `rule`) skrivs med alla varianters fält; fält som inte
  gäller den valda varianten skrivs som `null`, så att byte av doseringstyp inte lämnar
  gamla värden kvar.
- **Radering av ett helt dokument** görs bara via `delete(id)`, aldrig genom att skriva om det.
- Test: `CollectionContract` har fallen "okänt fält bevaras vid upsert" och "tömt valfritt
  fält försvinner vid upsert" (skill `firestore-data-layer`).

## Checklista: nytt fält på en befintlig modell

1. Fältet på `:core`-modellen **med default** (`= ""`, `= 0`, `= false`, `= null`, `= emptyList()`).
2. Fältet i codecens `encode` och `decode` via fälthjälparna – aldrig egen parsning.
   `encode` skriver fältet även när det saknar värde (`null`).
3. Tolka äldre dokument medvetet: saknas fältet → default. Behöver det härledas från
   andra fält, gör det i `decode` och testa det.
4. Fältets typ i samlingens `valid…`-funktion i `firestore.rules` (`optInt`, `optString` …)
   + rules-test; påverkar det vem som får läsa/skriva, även den regeln.
5. Tester (nedan). Uppdatera rundturstestets seed så att fältet har ett icke-default-värde.
6. KRAVLISTA: DAT-raden för modellen (regel 3).

## Checklista: ny samling

1. Modell + `DocCodec` i `:core`.
2. En rad i `Paths` och en `FirestoreCollection<T>` via `CollectionFactory` – ingen ny
   repository-mekanik (skill `firestore-data-layer`).
3. Egen `match` med `valid…`-funktion i `firestore.rules` + rules-test – en samling utan
   regel nekas (och skulle annars stoppa backupen, BCK-8).
4. En rad i `tools/db/lib/collections.mjs`.
5. Rundturstestets seed får minst ett dokument i samlingen.
6. KRAVLISTA: ny DAT-rad och BCK-raden som listar vad som ingår i backup.

## Tester som ALLTID krävs

| Test | Var | Skyddar |
|---|---|---|
| Hela codec-kontraktet | `core/src/test/.../schema/CodecsTest.kt` via `assertCodecContract(codec, sample, defaults)` – en rad per codec; nästlade via `assertVariantCodecContract` | provet har icke-default-värde i **varje** fält (kontrolleras med reflektion), rundtur, tolerans, okända fält och fullständig skrivning |
| Codec-rundtur | ingår i kontraktet: `assertCodecRoundTrip(codec, sample)` | modell → map → modell identisk, inklusive nya fält |
| Tolerans | samma test via `assertToleratesMissingFields` + `assertIgnoresUnknownFields` | äldre och nyare dokument |
| Fullständig skrivning | samma test via `assertEncodesAllFields(codec, sampleWithNulls)` | `encode` skriver varje känt fält, även `null`, så att merge tömmer och bevarar rätt |
| Migrering | `core/src/test/.../schema/SchemaMigratorTest.kt` | varje versionssteg |
| Rundtur end-to-end | `tools/db/test/roundtrip.test.mjs` **endast mot emulatorn** | seed → export → radera → import → export identisk |
| Samlingslistan | `tools/db/test/collections.test.mjs` | `collections.mjs` = `Paths` = rules |
| Appens export = tools/db-formatet | `core/.../schema/ExportFormatTest.kt` och `app/.../data/HouseholdExporterTest.kt` mot `tools/db/test/fixtures/household.json` | varje dokument och värde i rundturens testdata kommer ut exakt som `tools/db export` skriver det, alltså läsbart för `tools/db import` |

Varje persisterat fält ska kunna spåras till minst ett test som **asserterar på fältet**
med ett icke-default-värde.

### Tester rör aldrig den riktiga databasen

Rundturstestet raderar och importerar data. Därför:
- Alla tester i `tools/db/test/` går via en gemensam testhjälpare som **avbryter direkt om
  `FIRESTORE_EMULATOR_HOST` saknas**, och som använder projekt-ID:t `demo-reseapoteket`
  (Firebase-emulatorn behandlar `demo-*` som ett projekt utan koppling till molnet).
- `tools/db/lib/admin.mjs` i testläge godkänner **bara** `FIRESTORE_EMULATOR_HOST` satt och
  ett projekt-ID som börjar med `demo-` – oavsett hur testet startats. Testläge är när
  `NODE_TEST_CONTEXT` är satt eller när anroparen uttryckligen begär det; testhjälparen
  begär det alltid.
- Krav: BCK-4.
- Testdata (`tools/db/test/fixtures/household.json`) är **syntetisk** – aldrig utdrag ur den
  riktiga databasen.

## Fallgropar

- **Default saknas** på modellfältet → gamla dokument kraschar `decode`.
- **Egen parsning i en codec** i stället för fälthjälparna → toleransen skiljer sig mellan
  modeller och `cpdCheck` slår till.
- **`Timestamp` vs `Long` vs ISO-sträng:** datum utan tid lagras som ISO-sträng
  (`yyyy-MM-dd`); `Timestamp` bara för `createdAt`/`updatedAt`/`checkedAt`. `tools/db`
  serialiserar `Timestamp` som `{ "__ts": iso }`.
- **`null` vs saknat fält:** codecen skriver alla kända fält, även `null`, och `decode`
  behandlar `null` som saknat. Se *Skrivningar bevarar okända fält*.
- **Klockslag** (`travelDayTime`) lagras som `HH:mm` (`Doc.localTime`/`encodeTime`). Ett annat
  format – t.ex. sekunder från en framtida app – läses som "ingen egen tid" och skrivs som `null`
  vid nästa sparning. Byts formatet: höj `schemaVersion` och migrera, ändra aldrig formatet tyst.
- **Arrays av ID:n** (`travellerIds`, `memberUids`) – ordningen bevaras i rundturen.
- **Heltal vs decimaltal:** JSON skiljer dem inte åt – ett decimaltal med heltalsvärde (`2.0`)
  kommer tillbaka från en rundtur som heltal (`2`). Fälthjälparna i `:core` läser alla `Number`,
  så det är ofarligt; låt aldrig en codec bero på den skillnaden.
- **Batch-storlek:** Firestore tillåter 500 skrivningar per batch; import delar upp.
- **Samlingen glömd i `collections.mjs`** → den backas aldrig upp. Testet mot `Paths`
  fångar det; ta aldrig bort det testet.
- **Integritet:** logga aldrig dokumentinnehåll; backup krypteras alltid (skill
  `data-privacy-security`).
