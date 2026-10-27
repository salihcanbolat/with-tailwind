plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Puts the catalog's Kotlin Gradle plugin on the classpath so AGP's built-in Kotlin
    // and the Compose compiler plugin use the same Kotlin version as the root build.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
