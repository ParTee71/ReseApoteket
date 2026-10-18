import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

// google-services.json är hemlig och git-ignorerad. Saknas den (PR-bygge, session) byggs
// appen ändå: debug och release får stubbad Firebase-konfiguration (demo-projekt) från
// src/authStub (se android.sourceSets), så att appen kompilerar och startar – inloggning fungerar
// inte där. Release-stubben behövs för quality.yml:s minifierade bygge; release.yml skriver alltid
// den riktiga filen före bygget.
private val hasGoogleServices = file("google-services.json").exists()
if (hasGoogleServices) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

private val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

private val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

private fun signingValue(key: String) =
    localProps.getProperty("signing.$key") ?: providers.environmentVariable("SIGNING_${key.toScreamingSnake()}").orNull

private fun String.toScreamingSnake() = replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()

android {
    namespace = "se.partee71.reseapoteket"
    compileSdk = 37

    defaultConfig {
        applicationId = "se.partee71.reseapoteket"
        minSdk = 30
        targetSdk = 35
        versionCode = appVersion.getProperty("versionCode").toInt()
        versionName = appVersion.getProperty("versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val storePassword = signingValue("storePassword")
    val keyPassword = signingValue("keyPassword")
    val hasSigning = storePassword != null && keyPassword != null

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file("reseapoteket.jks")
                this.storePassword = storePassword
                keyAlias = signingValue("keyAlias") ?: "reseapoteket"
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    sourceSets {
        if (!hasGoogleServices) {
            getByName("debug").res.directories += "src/authStub/res"
            getByName("release").res.directories += "src/authStub/res"
        }
        // Kontraktstester som körs både i JVM (mot fakes) och på enhet (mot Firebase-emulatorn).
        getByName("test").kotlin.directories += "src/sharedTest/kotlin"
        getByName("androidTest").kotlin.directories += "src/sharedTest/kotlin"
    }

    buildFeatures {
        compose = true
        buildConfig = true // VERSION_NAME i Inställningar, DEBUG för komponentgalleriet
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
        }
    }
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

// Modellerna i :core byggs utan Compose-kompilatorn; filen säger att de är oföränderliga (skill compose-expert).
composeCompiler {
    stabilityConfigurationFiles.add(project.layout.projectDirectory.file("compose-stability.conf"))
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    // I appen, inte bara androidTest: Firebase hittar sina komponenter via appens manifest,
    // så auth-emulatorn i instrumenttesterna kräver det här. Används av inloggningen i #16.
    implementation(libs.firebase.auth)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime) // påminnelser inför resan (PÅM-6)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.konsist)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.work.testing)

    constraints {
        // AGP låser androidTest till appens versioner. När ett testberoende kräver en nyare
        // version än appen löser, höjs den här (skill android:android-gradle-logic).
        implementation(libs.androidx.concurrent.futures) // androidx.test.ext:junit
        implementation(libs.errorprone.annotations) // espresso via compose ui-test
    }

    // Instrumenttester: bara på begäran och i release (skill ci-budget); kompileras i PR.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// ExportFormatTest och HouseholdExporterTest läser tools/db:s testdata (SET-5) – en ändring där
// ska köra om testerna, även när de annars hämtas ur byggcachen.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("tools/db/test/fixtures"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("toolsDbFixtures")
}
