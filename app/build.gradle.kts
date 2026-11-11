plugins {
    // Kotlin itself is built into AGP 9; the Compose compiler plugin is not.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ember.xmb"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ember.xmb"
        // Fire OS 6 (Android 7.1) and up, which covers every current Fire TV device.
        minSdk = 25
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // The launcher is small; shrinking mainly trims Compose. Keep it on.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // Compose: foundation + ui only. Material is deliberately left out — every surface
    // here is custom-drawn, and Material's theming would only fight it.
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Android TV "Watch Next" provider, used by Continue Watching where available.
    implementation("androidx.tvprovider:tvprovider:1.1.0")
}
