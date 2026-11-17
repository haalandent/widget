plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Versjon fra tag (v1.2.3) via env VERSJON; versionCode = 1*10000 + 2*100 + 3.
val versjon: String = (System.getenv("VERSJON") ?: "0.0.1").removePrefix("v")
val versjonKode: Int = versjon.split(".").map { it.toIntOrNull() ?: 0 }
    .let { (it.getOrElse(0) { 0 } * 10000) + (it.getOrElse(1) { 0 } * 100) + it.getOrElse(2) { 0 } }
    .coerceAtLeast(1)

android {
    namespace = "no.haalandent.widget"
    compileSdk = 35

    defaultConfig {
        applicationId = "no.haalandent.widget"
        minSdk = 28
        targetSdk = 35
        versionCode = versjonKode
        versionName = versjon
    }

    // Signering bare når nøkkelen finnes (GitHub Actions secrets). Se README.
    val nokkel = System.getenv("WIDGET_KEYSTORE")
    signingConfigs {
        if (nokkel != null && file(nokkel).exists()) {
            create("release") {
                storeFile = file(nokkel)
                storeType = "pkcs12"
                storePassword = System.getenv("WIDGET_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WIDGET_KEY_ALIAS") ?: "widget"
                keyPassword = System.getenv("WIDGET_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
