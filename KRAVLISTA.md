# Kravlista – ReseApoteket (Android)

> Räknar ut hur mycket av familjens mediciner, kosttillskott och apoteksvaror som ska
> packas inför en resa, och låter familjen pricka av det som plockats fram.
>
> Paket: `se.partee71.reseapoteket` · Språk: svenska · Status: byggskelett (0.1.0) – inloggning och hushåll finns, övriga funktioner kommer.

Så här används listan: skill [`requirements-kravlista`](.claude/skills/requirements-kravlista/SKILL.md).
Krav märkta *(planerad)* är beslutade men inte byggda; markeringen tas bort i PR:en som
bygger kravet. Arkitekturbesluten bakom TP-kraven står i [ARKITEKTUR.md](ARKITEKTUR.md).

---

## 1. Översikt och syfte

| ID | Krav |
|----|------|
| ÖV-1 | Appen ska räkna fram ett **packförslag** av mediciner, kosttillskott och familjeartiklar utifrån resans längd i dagar och vilka som åker med. *(planerad)* |
| ÖV-2 | Appen ska ha en **packlista** där framplockade saker prickas av. *(planerad)* |
| ÖV-3 | Appen ska hantera **personer** med egna recept och kosttillskott, och **familjeartiklar** på hushållsnivå. *(planerad)* |
| ÖV-4 | Appen ska kräva **Google-inloggning**; all data hör till användarens hushåll. |
| ÖV-5 | Appen ska fungera **offline**: allt läses från den lokala Firestore-cachen och ändringar sparas direkt och synkas när nätet kommer tillbaka. |
| ÖV-6 | Hela gränssnittet ska vara på **svenska**. *(planerad)* |

---

## 2. Teknisk plattform

| ID | Krav |
|----|------|
| TP-1 | Enbart Android. minSdk, targetSdk och compileSdk anges i `app/build.gradle.kts` och i ARKITEKTUR.md → "Byggkonfiguration". |
| TP-2 | Gränssnitt i **Jetpack Compose** med **Material 3 Expressive** (API:er och versioner i ARKITEKTUR.md → "Verifierade API:er"). |
| TP-3 | **MVVM** med `StateFlow<UiState>` och sealed events, **Hilt** för DI. *(planerad, #20 – Hilt sedan #11, första ViewModel i #16)* |
| TP-4 | Två moduler: **`:core`** (ren Kotlin/JVM: modeller, codecs, beräkning) och **`:app`**. |
| TP-5 | Data lagras i **Cloud Firestore** med offline-cache (100 MB); enhetens inställningar i **DataStore**. |
| TP-6 | Inloggning via **Firebase Auth + Credential Manager**. |
| TP-7 | Navigering med **Navigation 3**. *(planerad, #20)* |
| TP-8 | Backup och databasåtkomst från utvecklingssessioner via Node-verktyget **`tools/db`**. *(planerad, #18 – läsverktygen; export, import och migrering finns sedan #17)* |

---

## 3. Navigation

| ID | Krav |
|----|------|
| NAV-1 | Toppnivå med fyra flikar – **Resor**, **Personer**, **Familjeartiklar**, **Inställningar** – i en flytande verktygsrad. *(planerad, #20)* |
| NAV-2 | Underskärmar döljer verktygsraden; fliktillstånd bevaras vid byte. *(planerad, #20)* |
| NAV-3 | Alla skärmbyten använder samma rörelse. *(planerad, #20)* |

---

## 4. Konto

| ID | Krav |
|----|------|
| AUTH-1 | Appen visar inloggningen ("Logga in med Google") tills användaren loggat in med sitt Google-konto; utloggad visas inget annat innehåll. |
| AUTH-2 | En avbruten inloggning – kontoväljaren stängd – är inget fel och visar inget meddelande. |
| AUTH-3 | Medan inloggningen och valet av hushåll pågår visar knappen "Loggar in …" och kan inte tryckas; ett fel visas som meddelande ovanför knappen (NFR-5). |
| AUTH-4 | Utloggning rensar den sparade inloggningen, Firebase-sessionen och det valda hushållet på enheten; är ingen inloggad rensas ett kvarglömt hushållsval alltid, så att nästa konto aldrig hamnar i ett annat kontos hushåll. *(planerad, #27 – knappen i Inställningar; stödet och rensningen finns sedan #16)* |

## 5. Hushåll

| ID | Krav |
|----|------|
| HUS-1 | Efter inloggning väljs ett hushåll där användaren är medlem; finns flera väljs det äldsta. Frågan går mot servern, så att en tom cache aldrig tolkas som "inget hushåll". |
| HUS-2 | Har användaren inget hushåll skapas "Mitt hushåll" med användaren som enda medlem, 2 marginaldagar och appens `schemaVersion`; det väljs först när servern tagit emot det. Utan nät skapas inget; felet visas och knappen försöker igen utan ny inloggning. |
| HUS-3 | Är hushållet sparat med en nyare `schemaVersion` än appen förstår visas bara "Uppdatera appen" med en knapp till senaste versionen (BCK-5). Appen visas inte förrän hushållets version är känd. |
| HUS-4 | Det valda hushållet sparas på enheten; nästa start går direkt in, även utan nät. |

## 6. Personer

*Fylls i av #21.*

## 7. Recept

*Fylls i av #22.*

## 8. Kosttillskott

*Fylls i av #23.*

## 9. Familjeartiklar

*Fylls i av #24.*

## 10. Resa

*Fylls i av #25.*

## 11. Beräkning

*Fylls i av #25. Formlerna står ordagrant som i skill [`packing-engine`](.claude/skills/packing-engine/SKILL.md).*

## 12. Packlista

*Fylls i av #26.*

## 13. Inställningar

*Fylls i av #20 (tema) och #27.*

## 14. Datamodell

Struktur och fält står bara i ARKITEKTUR.md → "Datamodell"; här står det som ska gälla.

| ID | Krav |
|----|------|
| DAT-1 | All data ligger under ett hushåll: `households/{hid}` med samlingarna `persons`, `persons/{pid}/regimens`, `familyItems`, `trips` och `trips/{tid}/packingItems`. |
| DAT-2 | Recept och kosttillskott är samma modell (`Regimen`, `kind`) och hör till en person; medicinens uppgifter ligger på receptet (inget separat register). |
| DAT-3 | En dosering är dagligen, vissa veckodagar, var n:e dag eller vid behov; en familjeartikels mängdregel är fast, per resenär eller per n dagar. |
| DAT-4 | Personer, recept, familjeartiklar och resor kan arkiveras och återställas på samma sätt (`archived`). |
| DAT-5 | Varje fält har ett standardvärde: ett äldre eller ofullständigt dokument kan alltid läsas, och okända fält ignoreras. |
| DAT-6 | En dosering eller mängdregel av okänd typ visas som "kan inte visas – uppdatera appen", ger aldrig en gissad mängd och skrivs tillbaka oförändrad. |
| DAT-7 | Datum utan tid lagras som `yyyy-MM-dd`; tidpunkter som Firestore-tidsstämplar. |

## 15. Backup och export

| ID | Krav |
|----|------|
| BCK-1 | All hushållsdata ska överleva en export → import-rundtur utan förlust (`tools/db export`/`import`, rundturstest mot emulatorn). |
| BCK-2 | En backup tas automatiskt varje vecka (`backup.yml`, måndag 03:00 UTC), krypteras med gpg (AES-256, integritetskontroll) och sparas i 90 dagar som artifact i det privata repot; klartexten lämnar aldrig jobbet. En misslyckad backup öppnar ett issue. |
| BCK-3 | En äldre appversion ska aldrig kunna radera fält som en nyare version har skrivit: skrivningar slås ihop med det lagrade dokumentet, och ett fält som användaren tömt försvinner ändå. |
| BCK-4 | Automatiska tester ska bara kunna köras mot Firebase-emulatorn (projekt-ID `demo-…`), aldrig mot den riktiga databasen. |
| BCK-5 | Hushållets `schemaVersion` anger dataformatet. Äldre format migreras vid läsning; nyare format än appen förstår läses men skrivs aldrig, och appen ber om uppdatering (HUS-3). Ett nytt enum-värde eller en ny dosering/regeltyp höjer versionen. |
| BCK-6 | Återställning: backupen dekrypteras till `tools/db/`, `import --dry-run` visar antal dokument per samling, och importen skriver sedan dokumenten exakt som i filen; med `--replace` tas dokument som tillkommit efter backupen bort, så att hushållet blir exakt som i backupen (README → Backup och återställning). |
| BCK-7 | Import kontrollerar hela filen innan något skrivs och vägrar hushåll vars `schemaVersion` är nyare än verktyget förstår, trasiga värden och sökvägar utanför de kända samlingarna. |
| BCK-8 | En samling som inte finns i samlingslistan (`tools/db/lib/collections.mjs`) – under ett hushåll eller i roten – stoppar exporten i stället för att tappas tyst, och värden som JSON inte kan bära exakt stoppar den i stället för att ändras. Undantag: JSON skiljer inte heltal från decimaltal, så ett decimaltal med heltalsvärde (t.ex. `2.0`) kommer tillbaka som heltal – appen läser båda likadant. En tom export ger fel. Felmeddelanden visar sökväg, aldrig innehåll. |

---

## 16. Icke-funktionella krav

### Gemensamma beteenden (gäller alla skärmar – funktionskraven refererar hit)

| ID | Krav |
|----|------|
| NFR-1 | **Listskärmar** visar laddning, tomt tillstånd med en primärknapp, fel med "Försök igen" och innehåll på samma sätt överallt. *(planerad, #20)* |
| NFR-2 | **Redigering:** "Spara" är aktiv först när uppgifterna är giltiga och ändrade; fältfel visas efter första försöket; bakåt med osparade ändringar frågar "Släng ändringar?"; sparfel visas som meddelande. *(planerad, #20)* |
| NFR-3 | **Arkivera/dölj** sker med svep och kan ångras i 5 sekunder. *(planerad, #20)* |
| NFR-4 | **Permanent radering** kräver alltid bekräftelse. *(planerad, #20)* |
| NFR-5 | **Felmeddelanden** har samma svenska formuleringar överallt; varje fel från datalagret har en egen text. *(planerad, #20 – texterna och mappningen finns sedan #15)* |
| NFR-6 | En **synkindikator** visas när ändringar väntar på att skickas; skrivningar som servern senare nekar visas som meddelande. *(planerad, #20 – synkläget finns sedan #15)* |

### Kvalitet, integritet och tester

| ID | Krav |
|----|------|
| NFR-7 | Samma typ av element ser ut och beter sig likadant överallt; kontrolleras automatiskt (skill `shared-ui-components`). *(planerad, #20 – kontrollerna finns sedan #11)* |
| NFR-8 | Hälsodata och personuppgifter loggas aldrig och lämnar bara appen via Firestore, den krypterade backupen och användarens egen export – aldrig via Androids backup eller enhetsöverföring (skill `data-privacy-security`). *(planerad – backup och enhetsöverföring avstängda sedan #11)* |
| NFR-9 | Tester finns på varje berörd nivå; flaky tester räknas som buggar (skill `testing-strategy`). *(planerad, #17 – rundtur mot emulatorn; övriga nivåer finns sedan #11 och #15)* |
| NFR-10 | En PR kostar högst ca 8 Actions-minuter och kör bara tester för det som ändrats; `master` kör alltid allt. Emulator, lint och release-bygge körs på begäran, veckovis och vid release (skill `ci-budget`). |
| NFR-11 | Gränssnittet fungerar med TalkBack och stor text; text har kontrast minst 4,5:1 (skill `accessibility-compose`). *(planerad, #20)* |
| NFR-12 | Release-bygget är minifierat; det byggs veckovis och före varje release (`quality.yml`). |
| NFR-13 | Skärmdumpar (ljust och mörkt) jämförs mot incheckade referensbilder i varje PR som rör appen; en oväntad skillnad fäller PR:en och diffbilden sparas (skill `testing-strategy`). |
| NFR-14 | Kopierad kod fäller bygget (`cpdCheck`, skill `shared-ui-components`). |
| NFR-15 | Dokumentationen kontrolleras i varje PR: CI-tabellen mot filtren, relativa länkar, nämnda skills och agenter, och hooktestet. |
| NFR-16 | Data i Firestore nås bara av inloggade medlemmar i hushållet (en användare kan lista just de hushåll där den själv är medlem): allt ligger under `households/{hid}`, ett hushåll skapas med skaparen som enda medlem, har alltid minst en medlem och medlemslistan kan inte ändras från appen (delning: FUT-2), raderas aldrig från appen och har en `schemaVersion` som heltal. Reglerna testas mot Firebase-emulatorn (`tools/db/test/rules.test.mjs`) och deployas manuellt. |

---

## 17. Framtida

| ID | Krav |
|----|------|
| FUT-1 | Påminnelser inför resa och när det är dags att packa. |
| FUT-2 | Dela hushållet med fler Google-konton. |
| FUT-3 | Slå upp mediciner via streckkod eller FASS. |
