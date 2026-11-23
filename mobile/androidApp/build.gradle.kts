plugins {
    id("com.android.application")
    kotlin("plugin.compose")
}

android {
    namespace = "de.wghub.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "de.wghub.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.13.0")
}
