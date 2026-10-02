# Kravlista – ReseApoteket (Android)

> Räknar ut hur mycket av familjens mediciner, kosttillskott och apoteksvaror som ska
> packas inför en resa, och låter familjen pricka av det som plockats fram.
>
> Paket: `se.partee71.reseapoteket` · Språk: svenska · Status: byggskelett (0.1.0) – inloggning, hushåll, backup, databasåtkomst, navigering med fyra flikar, tema och komponentkatalog finns; personer, recept, familjeartiklar, resor, packlistan och inställningarna finns sedan #21–#27.

Så här används listan: skill [`requirements-kravlista`](.claude/skills/requirements-kravlista/SKILL.md).
Krav märkta *(planerad)* är beslutade men inte byggda; markeringen tas bort i PR:en som
bygger kravet. Arkitekturbesluten bakom TP-kraven står i [ARKITEKTUR.md](ARKITEKTUR.md).

---

## 1. Översikt och syfte

| ID | Krav |
|----|------|
| ÖV-1 | Appen ska räkna fram ett **packförslag** av mediciner, kosttillskott och familjeartiklar utifrån resans längd i dagar och vilka som åker med. |
| ÖV-2 | Appen ska ha en **packlista** där framplockade saker prickas av (PAK). |
| ÖV-3 | Appen ska hantera **personer** med egna recept och kosttillskott, och **familjeartiklar** på hushållsnivå (PER, REC, KOS, FAM). |
| ÖV-4 | Appen ska kräva **Google-inloggning**; all data hör till användarens hushåll. |
| ÖV-5 | Appen ska fungera **offline**: allt läses från den lokala Firestore-cachen och ändringar sparas direkt och synkas när nätet kommer tillbaka. |
| ÖV-6 | Hela gränssnittet ska vara på **svenska**. |

---

## 2. Teknisk plattform

| ID | Krav |
|----|------|
| TP-1 | Enbart Android. minSdk, targetSdk och compileSdk anges i `app/build.gradle.kts` och i ARKITEKTUR.md → "Byggkonfiguration". |
| TP-2 | Gränssnitt i **Jetpack Compose** med **Material 3 Expressive** (API:er och versioner i ARKITEKTUR.md → "Verifierade API:er"). |
| TP-3 | **MVVM** med `StateFlow<UiState>` och sealed events, **Hilt** för DI. |
| TP-4 | Två moduler: **`:core`** (ren Kotlin/JVM: modeller, codecs, beräkning) och **`:app`**. |
| TP-5 | Data lagras i **Cloud Firestore** med offline-cache (100 MB); enhetens inställningar i **DataStore**. |
| TP-6 | Inloggning via **Firebase Auth + Credential Manager**. |
| TP-7 | Navigering med **Navigation 3**. |
| TP-8 | Backup och databasåtkomst från utvecklingssessioner via Node-verktyget **`tools/db`**: `stats`, `query` och `get` läser (med en läsnyckel, inget sparas på disk), `export`, `import` och `migrate` för backup och schemaändringar. |

---

## 3. Navigation

| ID | Krav |
|----|------|
| NAV-1 | Toppnivå med fyra flikar – **Resor**, **Personer**, **Familjeartiklar**, **Inställningar** – i en flytande verktygsrad. Appen startar på Resor. |
| NAV-2 | Underskärmar döljer verktygsraden; fliktillstånd bevaras vid byte. Tillbaka i en annan fliks rot går till Resor; tillbaka i Resor lämnar appen. Ett dubbeltryck öppnar en skärm en gång, och en skärm som stänger sig två gånger stänger aldrig skärmen under. |
| NAV-3 | Alla skärmbyten använder samma rörelse. |

---

## 4. Konto

| ID | Krav |
|----|------|
| AUTH-1 | Appen visar inloggningen ("Logga in med Google") tills användaren loggat in med sitt Google-konto; utloggad visas inget annat innehåll. |
| AUTH-2 | En avbruten inloggning – kontoväljaren stängd – är inget fel och visar inget meddelande. |
| AUTH-3 | Medan inloggningen och valet av hushåll pågår visar knappen "Loggar in …" och kan inte tryckas; ett fel visas som meddelande ovanför knappen (NFR-5). |
| AUTH-4 | Utloggning rensar den sparade inloggningen, Firebase-sessionen och det valda hushållet på enheten; är ingen inloggad rensas ett kvarglömt hushållsval alltid, så att nästa konto aldrig hamnar i ett annat kontos hushåll. Utloggningen görs från Inställningar (SET-4). |

## 5. Hushåll

| ID | Krav |
|----|------|
| HUS-1 | Efter inloggning väljs ett hushåll där användaren är medlem; finns flera väljs det äldsta. Frågan går mot servern, så att en tom cache aldrig tolkas som "inget hushåll". Byts konto medan valet pågår avbryts det, så att det nya kontot aldrig får det förras hushåll eller fel. |
| HUS-2 | Har användaren inget hushåll skapas "Mitt hushåll" med användaren som enda medlem, 2 marginaldagar och appens `schemaVersion`; det väljs först när servern tagit emot det. Utan svar från servern väljs inget; felet visas och knappen försöker igen utan ny inloggning – med samma hushåll, även efter en omstart, så att det aldrig blir två. |
| HUS-3 | Är hushållet sparat med en nyare `schemaVersion` än appen förstår visas bara "Uppdatera appen" med en knapp till senaste versionen (BCK-5). Appen visas inte förrän hushållets version är känd. |
| HUS-4 | Det valda hushållet sparas på enheten; nästa start går direkt in, även utan nät. En oläsbar eller trasig inställningsfil ger standardvärden i stället för att låsa appen. |
| HUS-5 | Går det valda hushållet inte att läsa – användaren är inte längre medlem eller hushållet är raderat – väljs det bort och hushållet väljs på nytt enligt HUS-1 och HUS-2; appen fastnar aldrig i laddning. Skrivningar under tiden nekas med behörighetsfel. |

## 6. Personer

| ID | Krav |
|----|------|
| PER-1 | Fliken **Personer** listar hushållets personer med emoji-avatar, namn och antal recept och kosttillskott som inte är arkiverade ("3 recept · 1 kosttillskott", annars "Inga recept eller kosttillskott än"); lista, tomt tillstånd och fel enligt NFR-1. "Visa arkiverade" i menyn visar arkiverade personer sist, märkta "Arkiverad". |
| PER-2 | En person har namn (obligatoriskt), emoji ur en kurerad lista på 40 och en av åtta personfärger, valda direkt i formuläret med en förhandsvisning av avataren; en ny person börjar med första emojin och färgen och hamnar sist i listan. Skapas och redigeras enligt NFR-2. |
| PER-3 | En person arkiveras med svep och kan ångras (NFR-3), eller arkiveras och återställs från persondetaljens meny. En arkiverad person föreslås inte i nya resor (BER-9); en befintlig packlista behåller personens rader tills listan räknas om (RES-4), och avprickade rader finns kvar även då. |
| PER-4 | Persondetaljen visar personens avatar, namn och antal överst, och personens **Recept** och **Kosttillskott** i var sin sektion med antal, dosering per rad och "Lägg till" (NFR-1). Arkiverade recept och kosttillskott visas inte. |

## 7. Recept

| ID | Krav |
|----|------|
| REC-1 | Ett recept har namn (obligatoriskt), styrka, form, enhet, förpackningsstorlek (valfri), dosering, anteckning och aktiv/inaktiv; skapas och redigeras enligt NFR-2. Form och enhet väljs var för sig. Ett nytt recept börjar som en tablett om dagen och hamnar sist. Radering frågar om receptets sparade namn. |
| REC-2 | Doseringen är **Dagligen**, **Veckodagar**, **Intervall** eller **Vid behov** (DAT-3). Giltig när: dagligen minst 1 dos, veckodagar minst en dag, intervall minst 2 dagar, vid behov minst 1; stegarna går inte under det. Felet visas när typens fält ändrats eller vid sparförsök, inte direkt vid byte av typ. En dosering av okänd typ (från en nyare app) visas som meddelande och bevaras (BCK-5). |
| REC-3 | Doseringen visas i klartext, samma text i lista (utom för pausade, REC-5), redigering och packlista, t.ex. "1 tablett morgon och kväll", "1 kapsel mån, ons, fre", "1 dos var 3:e dag", "vid behov: ta med 10". I persondetaljen visas dessutom doseringstypen som etikett. |
| REC-4 | Under doseringen visas vad som skulle packas för en 7-dagarsresa som börjar i dag, med hushållets marginaldagar (BER), t.ex. "För en 7-dagarsresa: 18 tabletter · 2 kartor" och förklaringen (BER-10). Går mängden inte att räkna ut visas skälet i stället för en mängd. |
| REC-5 | Inaktiva (pausade) recept visas nedtonade med etiketten "Pausat" och undertexten "Pausat – räknas inte med" i stället för doseringen, och räknas inte med i packförslaget (BER-9). Ett recept raderas permanent via menyn med bekräftelse (NFR-4). |

## 8. Kosttillskott

| ID | Krav |
|----|------|
| KOS-1 | Kosttillskott beter sig som recept (REC-1–5) i samma formulär, med egen sektion **Kosttillskott** med grön färg och bladikon, rubrikerna "Nytt kosttillskott"/"Redigera kosttillskott" och fältet **Dos** i stället för **Styrka**. |
| KOS-2 | Formerna är tablett, kapsel, pulver och droppar. Ett kosttillskott som sparats med en annan form visar den formen också. **Pulver** finns även för recept (REC-1) – samma former i hela appen; den kom med `schemaVersion` 2 (BCK-5, DAT-8). |
| KOS-3 | Ett nytt kosttillskott kan börja från snabbvalen **D-vitamin**, **Omega-3**, **Magnesium** och **Probiotika**, som bara fyller i namnet. Snabbvalen visas inte när ett befintligt redigeras. |

## 9. Familjeartiklar

| ID | Krav |
|----|------|
| FAM-1 | Fliken **Familjeartiklar** listar hushållets gemensamma saker grupperade per kategori i ordningen **Krämer & salvor**, **Plåster & förband**, **Verktyg**, **Övrigt**, med ikon och antal per kategori (NFR-1). Varje rad visar namn, mängdregeln i klartext och regeltypen som etikett; en pausad (inaktiv) artikel visas nedtonad med "Pausat" och räknas inte med i packförslaget, som recepten (REC-5). |
| FAM-2 | En familjeartikel har namn (obligatoriskt), kategori, enhet, mängdregel, anteckning och aktiv/inaktiv; skapas och redigeras enligt NFR-2 och en ny hamnar sist. Den arkiveras med svep och Ångra (NFR-3) eller från formulärets meny; "Visa arkiverade" visar arkiverade sist i sin kategori, och en arkiverad återställs från formulärets meny. |
| FAM-3 | Mängdregeln är **Fast antal**, **Per resenär** eller **Per N dagar** (DAT-3, BER-5–7), minst 1; samma regeltext i lista, redigering och packlista. Under regeln visas vad som skulle packas för hushållets personer (inte arkiverade) i 7 dagar med hushållets marginaldagar, t.ex. "För 4 resenärer i 7 dagar: 2 tuber", och förklaringen (BER-10). |
| FAM-4 | När listan är tom finns exemplen **Solkräm**, **Plåster** och **Febertermometer**. Ett exempel öppnar formuläret ifyllt med namn, kategori, enhet och en vanlig mängd (1 tub per 7 dagar, 10 per resenär, 1 st) – inget sparas förrän användaren sparar. |

## 10. Resa

| ID | Krav |
|----|------|
| RES-1 | Fliken **Resor** listar resor med senaste start först (samma start: namn i svensk ordning): namn, startdatum och antal dagar, status (**Planeras**, **Packad**, **Klar** – en klar resa nedtonad), resenärernas avatarer och packat ("9 / 16", dolda rader räknas inte). Går resenärer eller packat inte att läsa visas resorna ändå, utan dem. En resa arkiveras med svep och Ångra (NFR-3) eller från formulärets meny; "Visa arkiverade" visar dem sist. Tryck öppnar resans packlista (PAK); resan ändras därifrån med pennan. |
| RES-2 | En resa har namn ("Vart?"; lämnas det tomt blir det "Resa <datum>", t.ex. "Resa 2 okt"), startdatum (förval i morgon), antal dagar 1–60 (förval 7) och resenärer ("Vem åker med?", alla personer som inte är arkiverade förvalda; en arkiverad person visas bara om den åker med i den sparade resan); minst en resenär krävs. Skapas och redigeras enligt NFR-2. |
| RES-3 | Under resenärerna visas vad packlistan blir, t.ex. "För 4 resenärer i 7 dagar: 16 saker att packa – 6 recept · 3 kosttillskott · 7 familjeartiklar" (BER). En ny resa sparas med **"Skapa packlista"**, som räknar fram packlistan och öppnar den. |
| RES-4 | Ändras dagar, startdatum eller resenärer frågar Spara **"Räkna om packlistan?"**: **"Räkna om"** sparar och räknar om – föreslagna rader får nya förslag, en ändrad mängd (med sina förpackningar) och en avprickning behålls, egna rader rörs inte; en avprickad rad som inte längre finns i förslaget behålls med förklaringen "Inte längre i förslaget". **"Behåll listan"** sparar resan utan att röra packlistan; tryck utanför sparar ingenting. Går packlistan inte att räkna fram eller skriva sparas inte heller resan – formuläret står kvar med felet. |

## 11. Beräkning

Formlerna står ordagrant som i skill [`packing-engine`](.claude/skills/packing-engine/SKILL.md). Beräkningen finns i `:core` och används av resan (RES-3, RES-4) och förhandsvisningarna.

| ID | Krav |
|----|------|
| BER-1 | `effectiveDays = trip.days + settings.marginDays`; negativa värden räknas som 0 och vart och ett begränsas till 366 dagar, och en mängd till 99 999 (skydd mot trasiga data). |
| BER-2 | Dagligen: `dosesPerDay × unitsPerDose × effectiveDays`. |
| BER-3 | Veckodagar: antal datum `d` i `[startDate, startDate + effectiveDays)` där `d.dayOfWeek ∈ days`, × `unitsPerDose`; utan startdatum 0 + varning ("startdatum saknas – kan inte beräknas"). |
| BER-4 | Intervall: `ceil(effectiveDays / intervalDays) × unitsPerDose` (intervall under 1 räknas som 1); vid behov: `quantity`, och utan angiven mängd 0 + varning ("vid behov – ange hur många som ska med"); okänd dosering: 0 + varning ("kan inte beräknas – uppdatera appen"). |
| BER-5 | Fast antal: `quantity`. |
| BER-6 | Per resenär: `quantity × antal resenärer`. |
| BER-7 | Per N dagar: `ceil(effectiveDays / perDays) × quantity` (`perDays` under 1 räknas som 1). |
| BER-8 | Resultatet avrundas **uppåt** till helt antal enheter; `packages = ceil(quantity / packageSize)` när `packageSize` finns. |
| BER-9 | Bara aktiva, icke arkiverade recept och kosttillskott för resenärer som inte själva är arkiverade, och bara aktiva, icke arkiverade familjeartiklar, räknas med. Samma medicinnamn hos två personer ger två rader. |
| BER-10 | Varje rad har en förklaring, t.ex. "2 × 1 tablett/dag × (7 + 2) dagar = 18 tabletter → 2 kartor à 10"; dagarna visas som de räknas (BER-1), så att förklaringen aldrig motsäger mängden. |

## 12. Packlista

| ID | Krav |
|----|------|
| PAK-1 | Packlistan visar resans namn, startdatum, "7 dagar · 3 resenärer", resenärernas avatarer och packprogress "9 av 14 packat" (dolda rader räknas inte), och sektionerna **Mediciner** och **Kosttillskott** (per person, med personens namn och färg) och **Övrigt** (i två kolumner), var och en med packat/totalt. Pennan öppnar resans formulär (RES-2–4). |
| PAK-2 | Varje rad visar namn (för recept "namn styrka", t.ex. "Levaxin 50 µg"), mängd och enhet ("18 tabletter · 2 kartor") och för recept och kosttillskott doseringen i kort form ("1/dag", "vid behov"). Krysset prickar av – TalkBack läser namn och mängd; en avprickning sparar tidpunkten. Avprickade rader hamnar sist hos personen om inställningen SET-3 är på (förval på). |
| PAK-3 | Tryck på en rad öppnar mängden i ett bottom sheet med förslaget och förklaringen (BER-10); en ändrad mängd märks **"ändrad"** (utan förpackningar) och kan återställas till förslaget. |
| PAK-4 | **"Lägg till"** skapar en egen rad: vad, var (Mediciner, Kosttillskott eller Övrigt), vem (för mediciner och kosttillskott, en av resenärerna) och hur många. Menyn har **"Räkna om"** (frågar först, RES-4) och **"Dölj packade"**. En rad döljs med svep och ångra ("%s dold", NFR-3) och syns i den hopfällda sektionen **"Dolda"** sist, där **"Visa igen"** tar tillbaka den. Ett fel när en ändring sparas visas i den öppna panelen (ändra mängd, lägg till), annars som snackbar (NFR-3). |
| PAK-5 | När allt är packat visas konfetti och **"Allt packat! 🧳 Trevlig resa"** i grönt i stället för progressen, och resans status blir packad; blir listan ofullständig igen blir den planerad. Statusen följer listan först efter en egen ändring (avprickning, mängd, dölj, lägg till, räkna om) och sedan medan listan är öppen – att bara öppna listan ändrar ingenting. Bara status skrivs på resan, och avprickning, mängd och dölj skriver bara sitt fält på raden, så att en samtidig ändring på en annan enhet inte skrivs över; reseformuläret skriver i sin tur aldrig över statusen, och "Räkna om" där låter den följa den nya listan. En klar resa ändras inte, en lista utan synliga rader räknas inte som packad, och att dölja det packade packar inte upp resan. |

## 13. Inställningar

| ID | Krav |
|----|------|
| SET-1 | Tema **ljust**, **mörkt** eller **system**, sparat på enheten; ingen dynamisk färg. |
| SET-2 | **Hushållet:** namnet ändras i ett eget formulär (NFR-2); **marginaldagar** 0–7 (förval 2) med förklaringen "Extra dagar som läggs på varje resa, ifall något krånglar" ändras med +/− direkt i Inställningar och sparas på en gång. Var och en skriver bara sitt fält, så att en samtidig ändring av det andra står kvar; medlemmar och `schemaVersion` rörs aldrig. |
| SET-3 | **"Flytta avprickade sist"** i packlistan, sparat på enheten, förval på (PAK-2). |
| SET-4 | Kontot visar Google-kontots namn och e-post (de sparas eller loggas aldrig); **"Logga ut"** frågar först ("Logga ut?") och leder till inloggningen (AUTH-4). |
| SET-5 | **Data & backup:** text om synk och veckobackup, dataformat (`schemaVersion`) och appversion; **"Exportera som JSON"** sparar hushållets alla dokument i samma format som `tools/db export` – så att filen kan läsas in med `tools/db import` (BCK-1, BCK-6) – och öppnar Androids delningsmeny, bara efter användarens tryck. Bara den senaste exportfilen finns kvar i appens cache. Recept under en person som saknar eget dokument (bara möjligt via verktygen; appen visar dem aldrig) kommer bara med i veckobackupen. |
| SET-6 | **Om:** version och länk till källkoden; i debug-byggen öppnar "Komponenter" komponentgalleriet. |
| SET-7 | Ett fel från en åtgärd i Inställningar (marginaldagar, utloggning, export) visas i kortet där åtgärden gjordes, med samma texter som överallt (NFR-5), och försvinner vid nästa åtgärd. |

## 14. Datamodell

Struktur och fält står bara i ARKITEKTUR.md → "Datamodell"; här står det som ska gälla.

| ID | Krav |
|----|------|
| DAT-1 | All data ligger under ett hushåll: `households/{hid}` med samlingarna `persons`, `persons/{pid}/regimens`, `familyItems`, `trips` och `trips/{tid}/packingItems` – inga andra samlingar kan skapas (NFR-16). |
| DAT-2 | Recept och kosttillskott är samma modell (`Regimen`, `kind`) och hör till en person; medicinens uppgifter ligger på receptet (inget separat register). |
| DAT-3 | En dosering är dagligen, vissa veckodagar, var n:e dag eller vid behov; en familjeartikels mängdregel är fast, per resenär eller per n dagar. |
| DAT-4 | Personer, recept, familjeartiklar och resor kan arkiveras och återställas på samma sätt (`archived`). |
| DAT-5 | Varje fält har ett standardvärde: ett äldre eller ofullständigt dokument kan alltid läsas, och okända fält ignoreras. |
| DAT-6 | En dosering eller mängdregel av okänd typ visas som "kan inte visas – uppdatera appen", ger aldrig en gissad mängd och skrivs tillbaka oförändrad. |
| DAT-7 | Datum utan tid lagras som `yyyy-MM-dd`; tidpunkter som Firestore-tidsstämplar. |
| DAT-8 | Formerna för recept och kosttillskott är tablett, kapsel, flytande, pulver, inhalator, droppar, plåster och annat. Pulver kom i `schemaVersion` 2; inga befintliga dokument ändras av migreringen från 1. |

## 15. Backup och export

| ID | Krav |
|----|------|
| BCK-1 | All hushållsdata ska överleva en export → import-rundtur utan förlust (`tools/db export`/`import`, rundturstest mot emulatorn). |
| BCK-2 | En backup tas automatiskt varje vecka (`backup.yml`, måndag 03:00 UTC), krypteras med gpg (AES-256, integritetskontroll) och sparas i 90 dagar som artifact i det privata repot; klartexten lämnar aldrig jobbet. En misslyckad schemalagd backup öppnar ett issue. |
| BCK-3 | En äldre appversion ska aldrig kunna radera fält som en nyare version har skrivit: skrivningar slås ihop med det lagrade dokumentet, och ett fält som användaren tömt försvinner ändå. |
| BCK-4 | Automatiska tester ska bara kunna köras mot Firebase-emulatorn (projekt-ID `demo-…`), aldrig mot den riktiga databasen. |
| BCK-5 | Hushållets `schemaVersion` anger dataformatet. Äldre format migreras vid läsning; nyare format än appen förstår läses men skrivs aldrig, och appen ber om uppdatering (HUS-3). Ett nytt enum-värde eller en ny dosering/regeltyp höjer versionen. Versionen är minst 1 (ett lägre värde läses som 1), kan inte sänkas och höjs från appen högst till den högsta som finns. Ändrar ingen migrering dit några dokument stämplar appen själv ett äldre hushåll med sin version när det öppnas; annars gör `tools/db/migrate.mjs` det efter att alla dokument migrerats, och ett hushållsdokument som migreringen inte ändrar får bara den nya versionen. Ett tal utanför heltalsintervallet läses som gränsvärdet, aldrig som ett litet tal. |
| BCK-6 | Återställning: backupen dekrypteras till `tools/db/`, `import --dry-run` visar antal dokument per samling, och importen skriver sedan dokumenten exakt som i filen; med `--replace` tas dokument som tillkommit efter backupen bort, så att hushållet blir exakt som i backupen (README → Backup och återställning). |
| BCK-7 | Import kontrollerar hela filen innan något skrivs och vägrar hushåll vars `schemaVersion` är nyare än verktyget förstår, trasiga värden, sökvägar utanför de kända samlingarna, ogiltiga ID:n (tomma, `.`, `..`, `__…__`) och dubbletter. En migrering som inte klarar ett hushåll rapporterar det, migrerar de andra och avslutas med fel. |
| BCK-8 | En samling som inte finns i samlingslistan (`tools/db/lib/collections.mjs`) – under ett hushåll eller i roten – stoppar exporten i stället för att tappas tyst, och värden som JSON inte kan bära exakt stoppar den i stället för att ändras. Undantag: JSON skiljer inte heltal från decimaltal, så ett decimaltal med heltalsvärde (t.ex. `2.0`) kommer tillbaka som heltal – appen läser båda likadant. En tom export ger fel. Felmeddelanden visar sökväg, aldrig innehåll. |

---

## 16. Icke-funktionella krav

### Gemensamma beteenden (gäller alla skärmar – funktionskraven refererar hit)

| ID | Krav |
|----|------|
| NFR-1 | **Listskärmar** visar laddning, tomt tillstånd med en primärknapp (och där det finns exempel att börja från, som chips med plus ovanför, t.ex. FAM-4), fel med "Försök igen" och innehåll på samma sätt överallt. En lista som inte visats på en stund laddas om innan den visas, så att ett tidigare hushålls data aldrig syns efter kontobyte (NFR-8). |
| NFR-2 | **Redigering:** "Spara" är aktiv först när uppgifterna är giltiga och ändrade; fältfel visas när fältet ändrats eller efter ett sparförsök; uppgifter som inte går att läsa visas som fel med "Försök igen" (något som inte finns längre utan, NFR-5); bakåt med osparade ändringar – pilen eller systemets bakåt – frågar "Släng ändringar?", likaså Arkivera och Återställ i menyn; sparfel visas som meddelande. Medan uppgifterna laddas sparas inget, och ändras något medan det sparas står formuläret kvar med ändringen osparad i stället för att stängas. |
| NFR-3 | **Arkivera/dölj** sker med svep och kan ångras i 5 sekunder; samma åtgärd finns för TalkBack. Lämnar man skärmen under tiden är åtgärden klar – meddelandet visas inte igen. |
| NFR-4 | **Permanent radering** kräver alltid bekräftelse. |
| NFR-5 | **Felmeddelanden** har samma svenska formuleringar överallt; varje fel från datalagret har en egen text. Något som inte finns längre (raderat på en annan enhet) visas som det, utan "Försök igen". |
| NFR-6 | En **synkindikator** visas när ändringar väntar på att skickas; skrivningar som servern senare nekar visas som meddelande. *(planerad, saknar ännu issue – synkläget finns sedan #15)* |

### Kvalitet, integritet och tester

| ID | Krav |
|----|------|
| NFR-7 | Samma typ av element ser ut och beter sig likadant överallt; kontrolleras automatiskt (skill `shared-ui-components`). |
| NFR-8 | Hälsodata och personuppgifter loggas aldrig och lämnar bara appen via Firestore, den krypterade backupen och användarens egen export – aldrig via Androids backup eller enhetsöverföring (skill `data-privacy-security`). Användarens egen export (SET-5) delas bara via Androids delningsmeny och bara exportmappen i cachen kan delas. |
| NFR-9 | Tester finns på varje berörd nivå; flaky tester räknas som buggar (skill `testing-strategy`). |
| NFR-10 | En PR kostar högst ca 8 Actions-minuter och kör bara tester för det som ändrats; `master` kör alltid allt. Emulator, lint och release-bygge körs på begäran, veckovis och vid release (skill `ci-budget`). |
| NFR-11 | Gränssnittet fungerar med TalkBack och stor text; text har kontrast minst 4,5:1 (skill `accessibility-compose`, `ThemeContrastTest`). |
| NFR-12 | Release-bygget är minifierat; det byggs veckovis och före varje release (`quality.yml`). |
| NFR-13 | Skärmdumpar (ljust och mörkt) jämförs mot incheckade referensbilder i varje PR som rör appen; en oväntad skillnad fäller PR:en och diffbilden sparas (skill `testing-strategy`). |
| NFR-14 | Kopierad kod fäller bygget (`cpdCheck`, skill `shared-ui-components`). |
| NFR-15 | Dokumentationen kontrolleras i varje PR: CI-tabellen mot filtren, relativa länkar, nämnda skills och agenter, och hooktestet. |
| NFR-16 | Data i Firestore nås bara av inloggade medlemmar i hushållet (en användare kan lista just de hushåll där den själv är medlem): allt ligger under `households/{hid}`, ett hushåll skapas med skaparen som enda medlem, har alltid minst en medlem och medlemslistan kan inte ändras från appen (delning: FUT-2), raderas aldrig från appen och har en `schemaVersion` som heltal (BCK-5). Bara samlingarna i DAT-1 kan läsas och skrivas, och kända fält med fel typ nekas (saknade, tomma och okända fält är tillåtna, DAT-5). Reglerna testas mot Firebase-emulatorn (`tools/db/test/rules.test.mjs`) och deployas manuellt. |

---

## 17. Framtida

| ID | Krav |
|----|------|
| FUT-1 | Påminnelser inför resa och när det är dags att packa. |
| FUT-2 | Dela hushållet med fler Google-konton. |
| FUT-3 | Slå upp mediciner via streckkod eller FASS. |
