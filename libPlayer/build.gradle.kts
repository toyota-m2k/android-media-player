plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlinx.serialization)
    id("maven-publish")
}

group = "com.github.toyota-m2k"
version="1.0"

android {
    namespace = "io.github.toyota32k.lib.player"
    compileSdk = 34

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.kotlinx.serialization.json)

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
            version = "1.0"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
