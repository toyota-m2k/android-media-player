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
        mavenLocal()
        // Our own libraries live on JitPack, so it has to be tried before mavenCentral():
        // a 429 from Central disables that repository and aborts the whole resolution
        // before JitPack would ever be reached. Scope the filter to toyota-m2k so that
        // com.github.bumptech.glide keeps resolving from the Maven Central mirror instead
        // of making JitPack try to build bumptech/glide from source.
        maven(url = "https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.toyota-m2k.*") }
        }
        mavenCentral()
    }
}

rootProject.name = "android-media-player"
include(":libPlayer")
if (System.getenv("JITPACK") == null) {
    include(":app")
}