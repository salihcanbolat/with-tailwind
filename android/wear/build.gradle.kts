plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The Wear OS app (docs/08, design/Saat.dc.html). It shares the phone's application id, which the Data
// Layer API needs to pair the two apps, and the phone's device store and gauge drawing.
android {
    namespace = "com.damla.wear"
    compileSdk =
        libs.versions.androidCompileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.damla.app"
        minSdk =
            libs.versions.wearMinSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.androidTargetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_BASE_URL", quoted(setting("damla.apiBaseUrl", "http://10.0.2.2:8080")))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

fun setting(
    name: String,
    default: String,
): String = providers.gradleProperty(name).getOrElse(default)

fun quoted(value: String) = "\"" + value.replace("\"", "\\\"") + "\""

dependencies {
    implementation(project(":core"))
    implementation(project(":gauge"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.wear.complications.data.source)
    implementation(libs.androidx.wear.phone.interactions)
    implementation(libs.androidx.wear.remote.interactions)
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
}
