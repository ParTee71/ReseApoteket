---
name: release
description: ReseApotekets release-flöde — föreslå versionshöjning, bekräfta, uppdatera app/build.gradle.kts och README, landa release-commiten på master och trigga release.yml via workflow_dispatch så att GitHub Actions kör grindarna (tester, lint, instrumenttester), skapar taggen, bygger den signerade APK:n och publicerar en GitHub Release. CI-först (fungerar från telefonen). Använd BARA när användaren uttryckligen ber om en release. Trigger-ord: release, släpp, ny version, publicera, versionshöjning, bygg APK.
---

# Release

Kör stegen i ordning och rapportera efter varje steg. Stanna och förklara om något fallerar.
**Kör bara när användaren uttryckligen ber om en release.**

## Hur en release går till

- **Grindar:** `release.yml` kör alla tester, `cpdCheck`, dokumentkontroller,
  instrumenttester på emulator och `lintDebug` innan bygget – de steg som hålls utanför
  PR-flödet av CI-budgeten (skill `ci-budget`).
- **Bygg och signering:** i Actions; keystore och lösenord från secrets. Ingen lokal SDK krävs.
- **Publicering:** `release.yml` skapar taggen `vX.Y.Z` själv och publicerar en GitHub
  Release med changelog och APK. Två triggers: push av tagg, eller `workflow_dispatch` på
  `master` med `version_name` och `publish_release`.
- **Taggar pushas aldrig från en agentsession** – nätverkspolicyn blockerar `refs/tags/*`
  (HTTP 403). Använd dispatch-vägen.
- **Verktyg:** GitHub MCP (`mcp__github__*`); ingen `gh`.

## Steg 1 – Nuvarande version

Läs `versionCode` och `versionName` i `app/build.gradle.kts`.

## Steg 2 – Föreslå versionshöjning

Senaste tagg: `mcp__github__list_tags` / `mcp__github__get_latest_release` (owner `ParTee71`,
repo `ReseApoteket`). Commits sedan dess: `git log <tagg>..HEAD --oneline --no-merges`.

| Innehåll | Höjning |
|---|---|
| `BREAKING`, `!:`, borttagen funktion, schemaVersion höjd på ett sätt som kräver ny app | **major** |
| `feat`, ny skärm, ny förmåga | **minor** |
| `fix`, `chore`, `refactor`, `test`, `docs`, beroenden | **patch** |

Ny `versionCode` = nuvarande + 1. Visa:
```
Nuvarande: v<gammal>  (versionCode <gammal>)
Förslag:   v<ny>      (versionCode <ny>)
Skäl:      <en mening>
Commits:   <punktlista>
Kör v<ny>, eller en annan version?
```
**Vänta på bekräftelse.**

## Steg 3 – Uppdatera version, README och krav

1. `app/build.gradle.kts`: `versionCode` och `versionName`.
2. README "Versionshistorik": ny rad för `<ny>` med användarsynliga ändringar och `(#NN)`-referenser.
3. Kontrollera att KRAVLISTA.md speglar det som släpps (inga kvarvarande `*(planerad)*` för
   byggda funktioner) och att TP-kraven matchar SDK-nivåerna i byggfilen.
4. Har `schemaVersion` höjts sedan förra releasen: nämn det tydligt – äldre appar visar
   "Uppdatera appen" efter första skrivningen (skill `data-safety-backup`).

## Steg 4 – Release-commit på master

Via PR (grön och mergad), eller direkt till `master` om användaren uttryckligen godkänt det.
```
git commit -am "Release v<ny>"
```
Skapa ingen tagg här.

## Steg 5 – Trigga release-bygget

```
mcp__github__actions_run_trigger
  owner: ParTee71, repo: ReseApoteket
  workflow_id: release.yml, ref: master
  inputs: { version_name: "<ny>", publish_release: "true" }
```
`version_name` utan `v`; `publish_release` som strängen `"true"`. Workflowen vägrar om
taggen redan finns.

Följ körningen: `actions_list` → `actions_get` → vid fel `get_job_logs` (eller agenten
`ci-doktor`) → `get_release_by_tag` för `v<ny>`.

**Manuell reserv:** från användarens egen dator `git tag v<ny> && git push origin v<ny>`.

## Steg 6 – Sammanfattning

```
Släppt:  ReseApoteket v<ny>  (versionCode <ny>)
Commit:  Release v<ny> på master
Tagg:    v<ny> (skapad av release.yml)
CI:      <länk / status>
GitHub:  <Release-länk> (signerad APK bifogad)
```

## Reserv – bygga lokalt i Android Studio

`./gradlew :app:assembleRelease` kräver keystore i `app/` och signeringslösenord via
`local.properties` eller `SIGNING_*`-miljövariabler (skill `data-privacy-security`). För en
publicerad Release, kör ändå steg 5.
