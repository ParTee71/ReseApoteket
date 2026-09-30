# Kravlista – ReseApoteket (Android)

> Räknar ut hur mycket av familjens mediciner, kosttillskott och apoteksvaror som ska
> packas inför en resa, och låter familjen pricka av det som plockats fram.
>
> Paket: `se.partee71.reseapoteket` · Språk: svenska · Status: byggskelett (0.1.0) – inga funktioner än.

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
| ÖV-4 | Appen ska kräva **Google-inloggning**; all data hör till användarens hushåll. *(planerad)* |
| ÖV-5 | Appen ska fungera **offline**; ändringar synkas när nätet kommer tillbaka. *(planerad)* |
| ÖV-6 | Hela gränssnittet ska vara på **svenska**. *(planerad)* |

---

## 2. Teknisk plattform

| ID | Krav |
|----|------|
| TP-1 | Enbart Android. minSdk, targetSdk och compileSdk anges i `app/build.gradle.kts` och i ARKITEKTUR.md → "Byggkonfiguration". |
| TP-2 | Gränssnitt i **Jetpack Compose** med **Material 3 Expressive** (API:er och versioner i ARKITEKTUR.md → "Verifierade API:er"). |
| TP-3 | **MVVM** med `StateFlow<UiState>` och sealed events, **Hilt** för DI. *(planerad, #16, #20 – Hilt finns sedan #11)* |
| TP-4 | Två moduler: **`:core`** (ren Kotlin/JVM: modeller, codecs, beräkning) och **`:app`**. |
| TP-5 | Data lagras i **Cloud Firestore** med offline-cache; inställningar i **DataStore**. *(planerad, #15)* |
| TP-6 | Inloggning via **Firebase Auth + Credential Manager**. *(planerad, #16)* |
| TP-7 | Navigering med **Navigation 3**. *(planerad, #20)* |
| TP-8 | Backup och databasåtkomst från utvecklingssessioner via Node-verktyget **`tools/db`**. *(planerad, #17, #18)* |

---

## 3. Navigation

| ID | Krav |
|----|------|
| NAV-1 | Toppnivå med fyra flikar – **Resor**, **Personer**, **Familjeartiklar**, **Inställningar** – i en flytande verktygsrad. *(planerad, #20)* |
| NAV-2 | Underskärmar döljer verktygsraden; fliktillstånd bevaras vid byte. *(planerad, #20)* |
| NAV-3 | Alla skärmbyten använder samma rörelse. *(planerad, #20)* |

---

## 4. Konto

*Fylls i av #16.*

## 5. Hushåll

*Fylls i av #16.*

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

*Fylls i av #14 med serien DAT-1… Strukturen står i ARKITEKTUR.md → "Datamodell".*

## 15. Backup och export

| ID | Krav |
|----|------|
| BCK-1 | All hushållsdata ska överleva en export → import-rundtur utan förlust. *(planerad, #17)* |
| BCK-2 | En backup ska tas automatiskt varje vecka, krypteras och sparas i 90 dagar. *(planerad, #17)* |
| BCK-3 | En äldre appversion ska aldrig kunna radera fält som en nyare version har skrivit. *(planerad, #15)* |
| BCK-4 | Automatiska tester ska bara kunna köras mot Firebase-emulatorn (projekt-ID `demo-…`), aldrig mot den riktiga databasen. *(planerad, #17)* |

---

## 16. Icke-funktionella krav

### Gemensamma beteenden (gäller alla skärmar – funktionskraven refererar hit)

| ID | Krav |
|----|------|
| NFR-1 | **Listskärmar** visar laddning, tomt tillstånd med en primärknapp, fel med "Försök igen" och innehåll på samma sätt överallt. *(planerad, #20)* |
| NFR-2 | **Redigering:** "Spara" är aktiv först när uppgifterna är giltiga och ändrade; fältfel visas efter första försöket; bakåt med osparade ändringar frågar "Släng ändringar?"; sparfel visas som meddelande. *(planerad, #20)* |
| NFR-3 | **Arkivera/dölj** sker med svep och kan ångras i 5 sekunder. *(planerad, #20)* |
| NFR-4 | **Permanent radering** kräver alltid bekräftelse. *(planerad, #20)* |
| NFR-5 | **Felmeddelanden** har samma svenska formuleringar överallt. *(planerad, #15, #20)* |
| NFR-6 | En **synkindikator** visas när ändringar väntar på att skickas. *(planerad, #15)* |

### Kvalitet, integritet och tester

| ID | Krav |
|----|------|
| NFR-7 | Samma typ av element ser ut och beter sig likadant överallt; kontrolleras automatiskt (skill `shared-ui-components`). *(planerad, #20 – kontrollerna finns sedan #11)* |
| NFR-8 | Hälsodata och personuppgifter loggas aldrig och lämnar bara appen via Firestore, den krypterade backupen och användarens egen export – aldrig via Androids backup eller enhetsöverföring (skill `data-privacy-security`). *(planerad – backup och enhetsöverföring avstängda sedan #11)* |
| NFR-9 | Tester finns på varje berörd nivå; flaky tester räknas som buggar (skill `testing-strategy`). *(planerad, #15, #17 – JVM-, Robolectric-, skärmdumps- och Konsist-nivåerna finns sedan #11)* |
| NFR-10 | En PR kostar högst ca 8 Actions-minuter och kör bara tester för det som ändrats; `master` kör alltid allt. Emulator, lint och release-bygge körs på begäran, veckovis och vid release (skill `ci-budget`). |
| NFR-11 | Gränssnittet fungerar med TalkBack och stor text; text har kontrast minst 4,5:1 (skill `accessibility-compose`). *(planerad, #20)* |
| NFR-12 | Release-bygget är minifierat; det byggs veckovis och före varje release (`quality.yml`). |
| NFR-13 | Skärmdumpar (ljust och mörkt) jämförs mot incheckade referensbilder i varje PR som rör appen; en oväntad skillnad fäller PR:en och diffbilden sparas (skill `testing-strategy`). |
| NFR-14 | Kopierad kod fäller bygget (`cpdCheck`, skill `shared-ui-components`). |
| NFR-15 | Dokumentationen kontrolleras i varje PR: CI-tabellen mot filtren, relativa länkar, nämnda skills och agenter, och hooktestet. |

---

## 17. Framtida

| ID | Krav |
|----|------|
| FUT-1 | Påminnelser inför resa och när det är dags att packa. |
| FUT-2 | Dela hushållet med fler Google-konton. |
| FUT-3 | Slå upp mediciner via streckkod eller FASS. |
