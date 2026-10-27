import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

// The motion model is shared by the Android component, widget bitmaps and the reference renderer,
// so like :engine it stays free of platform code and targets Java 17.
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
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
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

// Renders the reference cases for tools/gauge-reference/compare.js.
tasks.register<JavaExec>("renderReference") {
    group = "verification"
    description = "Renders tools/gauge-reference/render-cases.json with the gauge painter as SVG."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "com.damla.gauge.ReferenceRenderKt"
    args(
        rootProject.file("tools/gauge-reference/render-cases.json").absolutePath,
        layout.buildDirectory
            .dir("reference-render")
            .get()
            .asFile.absolutePath,
    )
}
