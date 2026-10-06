pluginManagement {
    repositories {
        google()
        // Maven Central mirror on Google Cloud Storage. repo.maven.apache.org rate-limits
        // shared CI egress IPs (JitPack) with HTTP 429, and gradlePluginPortal() just
        // proxies that same failure through, so try the mirror first.
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        mavenLocal()
        maven (url="https://jitpack.io")
    }
}

rootProject.name = "android-media-player"
include(":libPlayer")
if (System.getenv("JITPACK") == null) {
    include(":app")
}