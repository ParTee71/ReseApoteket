---
name: android-gradle-logic
description: Use when setting up or refactoring Android Gradle build logic — convention plugins, composite builds, version catalogs, and shared build configuration across modules. Svenska trigger-ord: gradle, bygg, byggfil, beroende, lägga till bibliotek, versionskatalog, modul, :core, :app, plugin, byggtid, cpdCheck.
---

# Android Gradle Build Logic

Build logic lives in **one place** and every version in the version catalog – never
copy-pasted configuration across modules. The real setup (versions, SDK levels, where the
shared configuration lives, why) is in `ARKITEKTUR.md` → "Byggkonfiguration" – the only
source; this skill describes the rules, not the numbers.

## Version catalog rules

- Every dependency and plugin version is in `gradle/libs.versions.toml`; build files use
  `libs.…` only.
- BOMs keep families in sync: Compose BOM for Compose, Firebase BOM for Firebase (no
  `-ktx` artefacts). A library pinned outside its BOM (today `material3`, see
  `ARKITEKTUR.md` → "Verifierade API:er") gets a comment in the catalog saying why.
- AGP 9 has built-in Kotlin: `:app` does **not** apply `org.jetbrains.kotlin.android`. The
  Kotlin version comes from the `kotlin-jvm` plugin declared in the root `plugins {}`.
- AGP aligns `androidTest` dependencies with the app's resolved versions. When a test
  library needs a newer version than the app resolves ("Cannot find a version … strictly …"),
  raise it in the `constraints {}` block in `app/build.gradle.kts` with a comment naming the
  test library – never by disabling the alignment.
- No JVM toolchain: Java 17 is set via `sourceCompatibility`/`targetCompatibility` and
  Kotlin's `jvmTarget`, so any newer JDK can build.
- If more modules are ever added, move the shared configuration from the root
  `build.gradle.kts` to a `build-logic/` convention plugin (included with
  `pluginManagement { includeBuild("build-logic") }`) – still one place.

## This Project's Gradle Setup (ReseApoteket)

Two modules, built in #11:

| Module | Plugins | Rule |
|---|---|---|
| `:core` | `kotlin("jvm")`, `kotlin("plugin.serialization")` | Pure Kotlin/JVM. **No Android or Firebase dependencies**, ever – that is what keeps `:core:test` fast and runnable without an Android SDK. Dependencies: `kotlinx-serialization-json`, `kotlinx-datetime`; tests: `junit`, `kotlin-test`. |
| `:app` | `android.application` (AGP 9 has built-in Kotlin – no `kotlin.android`), `kotlin.compose`, `ksp`, `hilt`, `kotlin.serialization`, `google-services` (only when `app/google-services.json` exists), `roborazzi` | `implementation(project(":core"))`. Material 3 Expressive, Navigation 3, Firestore, Hilt. |

### Shared configuration lives in one place (rule 4)
Java 17 (no toolchain), Kotlin compiler flags, test timeout and test logging are configured **once** – in the root `build.gradle.kts` (`subprojects {}` with `plugins.withId {}`), chosen in #11 and documented in `ARKITEKTUR.md` → "Byggkonfiguration" – never copy-pasted into both module files. Move it to a `build-logic` convention plugin only if more modules are added.

### Root-level tasks
- `cpdCheck` (copy-paste detector, PMD CPD for Kotlin via PMD's CLI as a `JavaExec` task – the `de.aaschmid.cpd` plugin does not work with Gradle 9) over `core/src`, `app/src/main`, `app/src/test`, wired into `check`.
- Build performance flags in `gradle.properties`: `org.gradle.caching=true`, `org.gradle.configuration-cache=true`, `org.gradle.parallel=true` (skill `ci-budget`).

### AGP / Kotlin
- Use the latest stable AGP, Kotlin 2.x and Compose BOM when the skeleton is built; record versions in `ARKITEKTUR.md`.
- Use `kotlin.plugin.compose` (separate plugin from Kotlin 2.0+).
- KSP for annotation processing (Hilt) – KAPT is not used.

## Checklist

- [ ] All versions in `libs.versions.toml` – no hardcoded versions in build files
- [ ] Firebase uses the BOM to keep auth/firestore versions in sync
- [ ] `:core` has no Android/Firebase dependency
- [ ] Shared build settings exist once; module files only contain what is unique to the module
- [ ] `google-services` plugin applied only in `app/build.gradle.kts`; `google-services.json` is git-ignored and written from a secret in CI
- [ ] Builds without the secret still compile: when `app/google-services.json` is missing (PR builds from forks, sessions), a debug-only stub resource provides `default_web_client_id` (`app/src/authStub/res`, added to the debug source set only when the JSON is missing), so auth code compiles; sign-in simply fails at runtime there
- [ ] New dependency or plugin: build-time impact stated in the PR (skill `ci-budget`)
