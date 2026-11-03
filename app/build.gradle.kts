plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Fournis par GitHub Actions ; absents en local (Android Studio signe alors en debug).
val ciKeystore: String? = System.getenv("IVRY_KEYSTORE")
val ciVersion: Int = System.getenv("IVRY_VERSION_CODE")?.toIntOrNull() ?: 1

android {
    namespace = "fr.ivry.os"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.ivry.os"
        minSdk = 26
        targetSdk = 34
        versionCode = ciVersion
        versionName = "1.$ciVersion"
    }

    signingConfigs {
        create("release") {
            if (ciKeystore != null) {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("IVRY_KEYSTORE_PASSWORD")
                keyAlias = "ivry"
                keyPassword = System.getenv("IVRY_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ciKeystore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
