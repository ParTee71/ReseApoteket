---
name: android-gradle-logic
description: Use when setting up or refactoring Android Gradle build logic — convention plugins, composite builds, version catalogs, and shared build configuration across modules. Svenska trigger-ord: gradle, bygg, byggfil, beroende, lägga till bibliotek, versionskatalog, modul, :core, :app, plugin, byggtid, cpdCheck.
---

# Android Gradle Build Logic

Centralise build logic in reusable Convention Plugins instead of copy-pasting `build.gradle.kts` configuration across modules.

## Project Structure

```
root/
├── build-logic/
│   ├── convention/
│   │   ├── src/main/kotlin/
│   │   │   ├── AndroidApplicationConventionPlugin.kt
│   │   │   ├── AndroidLibraryConventionPlugin.kt
│   │   │   └── AndroidComposeConventionPlugin.kt
│   │   └── build.gradle.kts
│   └── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── app/
│   └── build.gradle.kts
└── settings.gradle.kts
```

## Step 1: Include `build-logic` as a Composite Build

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

## Step 2: Configure `build-logic/settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "build-logic"
include(":convention")
```

## Step 3: Write Convention Plugins

### Application Plugin

```kotlin
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<ApplicationExtension> {
                compileSdk = 35
                defaultConfig {
                    minSdk = 26
                    targetSdk = 35
                }
            }

            extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
                jvmToolchain(21)
            }
        }
    }
}
```

### Compose Plugin

```kotlin
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            val extension = extensions.getByType<CommonExtension<*, *, *, *, *, *>>()
            extension.buildFeatures.compose = true
        }
    }
}
```

## Version Catalog Best Practices

```toml
# gradle/libs.versions.toml
[versions]
agp = "8.13.2"
kotlin = "2.0.21"
compileSdk = "35"

[libraries]
# Use BOM for Firebase to keep versions in sync
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth-ktx" }  # version from BOM

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

## This Project's Gradle Setup (ReseApoteket)

Two modules, planned in #11:

| Module | Plugins | Rule |
|---|---|---|
| `:core` | `kotlin("jvm")`, `kotlin("plugin.serialization")` | Pure Kotlin/JVM. **No Android or Firebase dependencies**, ever – that is what keeps `:core:test` fast and runnable without an Android SDK. Dependencies: `kotlinx-serialization-json`, `kotlinx-datetime`; tests: `junit`, `kotlin-test`. |
| `:app` | `android.application`, `kotlin.android`, `kotlin.compose`, `ksp`, `hilt`, `kotlin.serialization`, `google-services` (only when `app/google-services.json` exists), `roborazzi` | `implementation(project(":core"))`. Material 3 Expressive, Navigation 3, Firestore, Hilt. |

### Shared configuration lives in one place (rule 4)
Java 17 toolchain, Kotlin compiler flags, test timeout and test logging are configured **once** – either in a `build-logic` convention plugin (structure above) or in the root `build.gradle.kts` – never copy-pasted into both module files. With only two modules a small convention plugin or a root `subprojects {}`/`plugins.withId {}` block is enough; pick one and document it in `ARKITEKTUR.md`.

### Root-level tasks
- `cpdCheck` (copy-paste detector, PMD CPD for Kotlin) over `core/src`, `app/src/main`, `app/src/test`, wired into `check`.
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
- [ ] New dependency or plugin: build-time impact stated in the PR (skill `ci-budget`)
