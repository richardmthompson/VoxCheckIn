import java.util.Properties
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    // json serialization
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0"
}

android {
    namespace = "com.example.voxmanifestorapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.voxcheckin"
        minSdk = 24
        targetSdk = 34
        versionCode = 22
        versionName = "2.22.1"
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.isFile }
                ?.inputStream()?.use { load(it) }
        }
        val key = (System.getenv("GEMINI_API_KEY") ?: local.getProperty("GEMINI_API_KEY", ""))
            .removeSurrounding("\"")
        require(key.all { it.isLetterOrDigit() || it == '_' || it == '-' }) { "Invalid Gemini key format" }
        buildConfigField("String", "GEMINI_API_KEY", "\"$key\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

    }

    signingConfigs {
        create("release") {
            storeFile =
                (System.getenv("RELEASE_STORE_FILE") ?: project.findProperty("RELEASE_STORE_FILE")?.toString())?.let {
                    file(it)
                }
            storePassword = (System.getenv("RELEASE_STORE_PASSWORD") ?: project.findProperty("RELEASE_STORE_PASSWORD")).toString()
            keyAlias = (System.getenv("RELEASE_KEY_ALIAS") ?: project.findProperty("RELEASE_KEY_ALIAS")).toString()
            keyPassword = (System.getenv("RELEASE_KEY_PASSWORD") ?: project.findProperty("RELEASE_KEY_PASSWORD")).toString()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // allow custom build config (for gemini api key)
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.7"
    }
    testOptions {
        // android.util.Log calls throughout production code (ViewModels, repos)
        // must no-op rather than throw in JVM unit tests.
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation(libs.androidx.room.common)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")

    // ksp replacing kapt
    ksp("androidx.room:room-compiler:2.6.1")

    // google speech to text
    val composeBom = platform("com.google.cloud:libraries-bom:26.48.0")
    implementation(composeBom)

    implementation("com.google.cloud:google-cloud-speech")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.28.0")
    implementation("io.grpc:grpc-okhttp:1.68.0")
    implementation("io.grpc:grpc-stub:1.68.0")

    // google text to speech
    implementation(libs.google.cloud.texttospeech)

    // google gemini for agent brain
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // data store for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")

}
