pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "lessive"

include(":protocol", ":app")

// Optional module with program names and error codes. It is not part of this repository;
// without it the app builds and uses the dial editor's names.
if (file("datapack/build.gradle.kts").exists()) include(":datapack")
