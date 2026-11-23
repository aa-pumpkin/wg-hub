plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
}

val ktor = "3.6.1"

kotlin {
    android {
        namespace = "de.wghub.app.shared"
        compileSdk = 37
        minSdk = 26
        androidResources { enable = true }
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
    }
    // iOS: add iosArm64() and iosSimulatorArm64() here once a Mac is available,
    // plus an iosMain FileStore and deviceLanguage. Everything else is shared as is.

    sourceSets.all {
        languageSettings {
            optIn("kotlin.time.ExperimentalTime")
            optIn("androidx.compose.material3.ExperimentalMaterial3Api")
            optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation("io.ktor:ktor-client-core:$ktor")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:$ktor")
            implementation("androidx.activity:activity-compose:1.13.0")
        }
    }
}

compose.resources {
    packageOfResClass = "de.wghub.app.generated"
}

// The app's texts come from the website's translation files.
val syncTexts by tasks.registering(Exec::class) {
    workingDir = rootDir
    commandLine("node", "scripts/sync-texts.mjs")
    inputs.dir(rootDir.resolve("../frontend/src/locales"))
    outputs.file("src/commonMain/composeResources/files/texts.json")
}
tasks.matching { it.name.startsWith("copyNonXmlValueResources") || it.name.startsWith("prepareComposeResources") }
    .configureEach { dependsOn(syncTexts) }
