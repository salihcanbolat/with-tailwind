plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// The gauge painter of :gauge compiled to JavaScript for the desktop companion (docs/08), so every
// surface draws the drop with the same code. Only the drop's own files are taken; the overlay scene and
// widget layout stay JVM only.
kotlin {
    js {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
        compilerOptions {
            moduleKind = org.jetbrains.kotlin.gradle.dsl.JsModuleKind.MODULE_ES
            target = "es2015"
        }
    }

    sourceSets {
        jsMain {
            kotlin.srcDir("../gauge/src/main/kotlin")
            kotlin.exclude("**/Overlay*.kt", "**/WidgetArrangement.kt", "**/Haptic.kt")
        }
    }
}

// Use the Node.js and npm on the PATH instead of downloading them (the build allows no project repositories).
rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().download.set(false)
}
rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec>().download.set(false)
}
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().download.set(false)
}
