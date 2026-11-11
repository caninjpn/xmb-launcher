// Top-level build file.
//
// AGP 9 compiles Kotlin itself — applying org.jetbrains.kotlin.android alongside it is
// rejected outright ("no longer required for Kotlin support since AGP 9.0").
// The Compose compiler plugin is still separate and still required whenever
// android.buildFeatures.compose is on.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
