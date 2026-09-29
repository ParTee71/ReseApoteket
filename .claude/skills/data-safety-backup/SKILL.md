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
          tools/db import  ←  backup.json (krypterad i Actions)  ←  tools/db export
```

| Led | Fil | Ansvar |
|---|---|---|
| Modell | `core/.../model/` | `data class` med **default på alla icke-obligatoriska fält**; `Identified`, `Sortable`, `Archivable` där det passar |
| Codec | `core/.../schema/*Codec.kt` | `encode`/`decode` byggda **enbart** av de delade fälthjälparna (`map.string`, `map.int`, `map.enum`, `map.localDate`, `map.nested`, `map.stringList` …) |
| Schema | `core/.../schema/Schema.kt`, `SchemaMigrator.kt` | `Schema.CURRENT_VERSION`; migreringssteg per version |
| Åtkomst | `app/.../data/firestore/FirestoreCollection.kt`, `Paths.kt` | Enda stället som talar med Firestore; sökvägar på ett ställe |
| Regler | `firestore.rules` | Varje samling under `households/{hid}` skyddad av `memberUids` |
| Samlingslista | `tools/db/lib/collections.mjs` | Enda listan export/import/query går igenom; testad mot `Paths` och rules |
| Backup | `.github/workflows/backup.yml` | Veckovis export, krypterad artifact, 90 dagar |

## Schemaversion

- `households/{hid}.schemaVersion` anger dataformatet.
- **Högre än `Schema.CURRENT_VERSION`** → appen visar "Uppdatera appen" och skriver
  ingenting (en gammal app får aldrig skriva över nyare data).
- **Lägre** → lat migrering i `SchemaMigrator` vid läsning; skrivning sker i nytt format.
- Höjs versionen: nytt steg i `SchemaMigrator` **och** spegling i `tools/db/migrate.mjs`,
  test per steg, och `lib/schema.mjs` följer med (ett test jämför konstanterna).
- De flesta ändringar kräver **ingen** versionshöjning: ett nytt valfritt fält med default
  läses redan av gamla dokument. Höj bara vid betydelseändring (omdöpt fält, ändrad enhet,
  ändrad struktur).

## Checklista: nytt fält på en befintlig modell

1. Fältet på `:core`-modellen **med default** (`= ""`, `= 0`, `= false`, `= null`, `= emptyList()`).
2. Fältet i codecens `encode` och `decode` via fälthjälparna – aldrig egen parsning.
3. Tolka äldre dokument medvetet: saknas fältet → default. Behöver det härledas från
   andra fält, gör det i `decode` och testa det.
4. Påverkar fältet vem som får läsa/skriva? Uppdatera `firestore.rules` + rules-test.
5. Tester (nedan). Uppdatera rundturstestets seed så att fältet har ett icke-default-värde.
6. KRAVLISTA: DAT-raden för modellen (regel 3).

## Checklista: ny samling

1. Modell + `DocCodec` i `:core`.
2. En rad i `Paths` och en `FirestoreCollection<T>` via `CollectionFactory` – ingen ny
   repository-mekanik (skill `firestore-data-layer`).
3. `firestore.rules`-regel + rules-test.
4. En rad i `tools/db/lib/collections.mjs`.
5. Rundturstestets seed får minst ett dokument i samlingen.
6. KRAVLISTA: ny DAT-rad och BCK-raden som listar vad som ingår i backup.

## Tester som ALLTID krävs

| Test | Var | Skyddar |
|---|---|---|
| Codec-rundtur | `core/src/test/.../schema/*CodecTest.kt` via `assertCodecRoundTrip(codec, sample)` | modell → map → modell identisk, inklusive nya fält |
| Tolerans | samma test via `assertToleratesMissingFields` + `assertIgnoresUnknownFields` | äldre och nyare dokument |
| Migrering | `core/src/test/.../schema/SchemaMigratorTest.kt` | varje versionssteg |
| Rundtur end-to-end | `tools/db/test/roundtrip.test.mjs` mot emulatorn | seed → export → radera → import → export identisk |
| Samlingslistan | `tools/db/test/collections.test.mjs` | `collections.mjs` = `Paths` = rules |

Varje persisterat fält ska kunna spåras till minst ett test som **asserterar på fältet**
med ett icke-default-värde.

## Fallgropar

- **Default saknas** på modellfältet → gamla dokument kraschar `decode`.
- **Egen parsning i en codec** i stället för fälthjälparna → toleransen skiljer sig mellan
  modeller och `cpdCheck` slår till.
- **`Timestamp` vs `Long` vs ISO-sträng:** datum utan tid lagras som ISO-sträng
  (`yyyy-MM-dd`); `Timestamp` bara för `createdAt`/`updatedAt`/`checkedAt`. `tools/db`
  serialiserar `Timestamp` som `{ "__ts": iso }`.
- **`null` vs saknat fält:** codecen skriver inte `null` för valfria fält som saknar värde.
- **Arrays av ID:n** (`travellerIds`, `memberUids`) – ordningen bevaras i rundturen.
- **Batch-storlek:** Firestore tillåter 500 skrivningar per batch; import delar upp.
- **Samlingen glömd i `collections.mjs`** → den backas aldrig upp. Testet mot `Paths`
  fångar det; ta aldrig bort det testet.
- **Integritet:** logga aldrig dokumentinnehåll; backup krypteras alltid (skill
  `data-privacy-security`).
