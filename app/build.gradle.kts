import java.net.HttpURLConnection
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing credentials live outside the repo entirely (never
// committed, not even gitignored-in-repo — see keystore.properties'
// storeFile path). Absent for anyone else cloning this public repo, in
// which case the release build type simply falls back to unsigned,
// which is the correct behavior for a clone that doesn't have the key.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

// A fresh joke per build, shown on the About screen — fetched from the
// backend's public GET /api/v1/joke at Gradle configuration time, not at
// app runtime, so it needs no network permission/error-state handling in
// the app itself. Falls back to a fixed joke if the backend isn't
// reachable (e.g. an offline build), rather than failing the build.
fun fetchDadJoke(baseUrl: String): String {
    val fallback = "Why don't scientists trust atoms? Because they make up everything."
    return try {
        val connection = URI("${baseUrl}api/v1/joke").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.setRequestProperty("Accept", "application/json")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        Regex("\"joke\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(body)
            ?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
            ?: fallback
    } catch (e: Exception) {
        fallback
    }
}

fun String.toJavaStringLiteral(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "")}\""

android {
    namespace = "ch.mcfx.urs"
    compileSdk = 37

    defaultConfig {
        applicationId = "ch.mcfx.urs"
        minSdk = 26
        targetSdk = 37
        versionCode = 14
        versionName = "0.13.0"
        buildConfigField(
            "String",
            "BUILD_TIME",
            "\"${SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(Date())}\"",
        )
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Distinct applicationId so a debug build installs as a
            // separate app next to the signed release install, instead
            // of conflicting with it over the same package name/signature.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            val baseUrl = "https://urs-backend-stg.mcfx.ch/"
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
            buildConfigField("String", "JOKE_OF_THE_DAY", fetchDadJoke(baseUrl).toJavaStringLiteral())
        }
        release {
            val baseUrl = "https://urs-backend.mcfx.ch/"
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
            buildConfigField("String", "JOKE_OF_THE_DAY", fetchDadJoke(baseUrl).toJavaStringLiteral())
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.biometric)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.wireguard.tunnel)
    implementation(libs.zxing.android.embedded)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.osmdroid.android)
    implementation(libs.nanohttpd)
}
