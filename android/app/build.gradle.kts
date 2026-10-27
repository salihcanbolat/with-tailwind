plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.damla.app"
    compileSdk =
        libs.versions.androidCompileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.damla.app"
        minSdk =
            libs.versions.androidMinSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.androidTargetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"

        // Backend and Firebase settings come from Gradle properties (e.g. ~/.gradle/gradle.properties), so no
        // project keys live in the repository. Without Firebase values the app runs without pushes.
        buildConfigField("String", "API_BASE_URL", quoted(setting("damla.apiBaseUrl", "http://10.0.2.2:8080")))
        buildConfigField("String", "FIREBASE_APP_ID", quoted(setting("damla.firebase.appId", "")))
        buildConfigField("String", "FIREBASE_API_KEY", quoted(setting("damla.firebase.apiKey", "")))
        buildConfigField("String", "FIREBASE_PROJECT_ID", quoted(setting("damla.firebase.projectId", "")))
        buildConfigField("String", "FIREBASE_SENDER_ID", quoted(setting("damla.firebase.senderId", "")))
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
    implementation(project(":widget"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("com.damla:engine")
    implementation("com.damla:alarms")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
}
