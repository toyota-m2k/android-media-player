import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.android.library)
    id("maven-publish")
}

group = "com.github.toyota-m2k"
version="1.0"

configure<LibraryExtension> {
    namespace = "io.github.toyota32k.lib.player"
    compileSdk {
        version = release(37)
        compileSdkMinor = 1
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

}

kotlin {
    jvmToolchain(21)
}


dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.glide)
    implementation(libs.okhttp)
    implementation(libs.okhttp3.integration)

    implementation(libs.android.logger)
    implementation(libs.android.utilities)
    implementation(libs.android.binding)
    implementation(libs.android.viewex)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// ./gradlew publishToMavenLocal
publishing {
    publications {
        // Creates a Maven publication called "release".
        register<MavenPublication>("release") {
            groupId = "com.github.toyota-m2k"
            artifactId = "android-media-player"
            version = project.findProperty("githubReleaseTag") as String? ?: "LOCAL"
            afterEvaluate {
                from(components["release"])
                artifact(tasks.named("sourceReleaseJar"))
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/toyota-m2k/android-media-player")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("TOKEN")
            }
        }
    }
}
