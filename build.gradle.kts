// Root build file — AREENAX native Android app (full Kotlin + Jetpack Compose port of the
// AREENAX web user panel; no WebView anywhere).
// Plugin versions are managed centrally in gradle/libs.versions.toml (Version Catalog).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
}
