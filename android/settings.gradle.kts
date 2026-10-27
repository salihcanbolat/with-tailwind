// Separate Gradle build for the Android modules. It needs the Android SDK and Google's Maven
// repository, which the root (engine + backend) build must not depend on. The root build is
// included so that `com.damla:engine` resolves to the local :engine project.
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "damla-android"

includeBuild("..")

include(":app", ":core", ":gauge", ":widget")
