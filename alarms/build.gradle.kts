import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

// Alarm evaluation for the backend and the Turkish alarm sentences for the apps (docs/07), so like the
// engine it stays platform free and targets Java 17.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

dependencies {
    api(project(":api-model"))
    api(project(":engine"))
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // The tests read the app's own sentence templates from strings.xml.
    systemProperty("damla.appStrings", rootProject.file("android/app/src/main/res/values/strings.xml").absolutePath)
}

kover {
    reports {
        verify {
            rule {
                minBound(100, CoverageUnit.BRANCH)
            }
        }
    }
}
