pluginManagement {
    repositories {
        google()
        mavenCentral()
        // Backup copy of Maven Central, used if the main one fails to answer.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
    }
}
rootProject.name = "Pocket-Theater"
include(":app")
