plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
}

import java.util.Properties

val localProps = Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
}

android {
    namespace = "com.danis.nadi"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.danis.nadi"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("nadi-release.jks")
            storePassword = localProps.getProperty("KEYSTORE_PASS") ?: System.getenv("KEYSTORE_PASS") ?: ""
            keyAlias = "nadi-key"
            keyPassword = localProps.getProperty("KEY_PASS") ?: System.getenv("KEY_PASS") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.nanohttpd)
    implementation(libs.nanohttpd.websocket)
    implementation(libs.zxing.core)
    implementation(libs.okhttp)
    implementation(libs.material) // diperlukan untuk XML theme (Theme.Material3.DayNight.NoActionBar)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    annotationProcessor(libs.androidx.room.compiler)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)

    testImplementation("org.json:json:20240303")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
