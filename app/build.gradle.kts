import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

/**
 * ÐšÐ»ÑŽÑ‡ Ð¿Ð¾Ð´Ð¿Ð¸ÑÐ¸ Ð»ÐµÐ¶Ð¸Ñ‚ Ñ€ÑÐ´Ð¾Ð¼ Ñ Ð¿Ñ€Ð¾ÐµÐºÑ‚Ð¾Ð¼ Ð¸ Ð² Ñ€ÐµÐ¿Ð¾Ð·Ð¸Ñ‚Ð¾Ñ€Ð¸Ð¹ Ð½Ðµ ÐµÐ´ÐµÑ‚.
 * Ð‘ÐµÐ· Ð½ÐµÐ³Ð¾ ÑÐ¾Ð±Ð¸Ñ€Ð°ÐµÑ‚ÑÑ Ñ‚Ð¾Ð»ÑŒÐºÐ¾ Ð¾Ñ‚Ð»Ð°Ð´Ð¾Ñ‡Ð½Ð°Ñ ÑÐ±Ð¾Ñ€ÐºÐ° â€” Ñ€ÐµÐ»Ð¸Ð·Ð½Ð°Ñ Ð¿Ñ€Ð¾ÑÑ‚Ð¾
 * Ð¾ÑÑ‚Ð°Ð½ÐµÑ‚ÑÑ Ð½ÐµÐ¿Ð¾Ð´Ð¿Ð¸ÑÐ°Ð½Ð½Ð¾Ð¹, Ð° Ð½Ðµ ÑÐ»Ð¾Ð¼Ð°ÐµÑ‚ ÑÐ±Ð¾Ñ€ÐºÑƒ.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "eu.bolh.app"
    // Ð¢Ð¾Ð»ÑŒÐºÐ¾ Ð²Ñ‹Ð¿ÑƒÑ‰ÐµÐ½Ð½Ñ‹Ðµ ÑƒÑ€Ð¾Ð²Ð½Ð¸ API. ÐŸÑ€ÐµÐ´Ð²Ð°Ñ€Ð¸Ñ‚ÐµÐ»ÑŒÐ½Ð°Ñ Ð¿Ð»Ð°Ñ‚Ñ„Ð¾Ñ€Ð¼Ð° (android-37,
    // Android 17) Ð¾ÑÑ‚Ð°Ð²Ð»ÑÐµÑ‚ Ð² Ð¼Ð°Ð½Ð¸Ñ„ÐµÑÑ‚Ðµ ÑÐ²Ð¾ÑŽ Ð¼ÐµÑ‚ÐºÑƒ â€” compileSdkVersionCodename
    // Ð¸ platformBuildVersionName, â€” Ð¸ ÑƒÑÑ‚Ð°Ð½Ð¾Ð²Ñ‰Ð¸ÐºÐ¸ Ð¾Ð±Ñ‹Ñ‡Ð½Ñ‹Ñ… Ñ‚ÐµÐ»ÐµÑ„Ð¾Ð½Ð¾Ð² Ñ‚Ð°ÐºÐ¾Ð¹ Ð¿Ð°ÐºÐµÑ‚
    // Ð½Ðµ Ñ€Ð°Ð·Ð±Ð¸Ñ€Ð°ÑŽÑ‚: Â«ÐÐµ ÑƒÐ´Ð°Ð»Ð¾ÑÑŒ Ð¾Ð±Ñ€Ð°Ð±Ð¾Ñ‚Ð°Ñ‚ÑŒ Ð¿Ð°ÐºÐµÑ‚Â». Ð’ÐµÑ€ÑÐ¸Ð¸ Ð±Ð¸Ð±Ð»Ð¸Ð¾Ñ‚ÐµÐº Ð²
    // libs.versions.toml Ð¿Ð¾Ð´Ð¾Ð±Ñ€Ð°Ð½Ñ‹ Ñ‚Ð°Ðº, Ñ‡Ñ‚Ð¾Ð±Ñ‹ Ð¸Ð¼ Ñ…Ð²Ð°Ñ‚Ð°Ð»Ð¾ 36.
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "eu.bolh.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
            // Ð’ÑÐµ ÑÑ…ÐµÐ¼Ñ‹ Ð¿Ð¾Ð´Ð¿Ð¸ÑÐ¸ ÑÑ€Ð°Ð·Ñƒ. ÐŸÐ¾ ÑƒÐ¼Ð¾Ð»Ñ‡Ð°Ð½Ð¸ÑŽ Ð¿Ñ€Ð¸ minSdk 26 Ð¾ÑÑ‚Ð°Ñ‘Ñ‚ÑÑ
            // Ñ‚Ð¾Ð»ÑŒÐºÐ¾ v2, Ð¸ ÑƒÑÑ‚Ð°Ð½Ð¾Ð²Ñ‰Ð¸ÐºÐ¸ Ð½ÐµÐºÐ¾Ñ‚Ð¾Ñ€Ñ‹Ñ… Ð¿Ñ€Ð¾ÑˆÐ¸Ð²Ð¾Ðº Ñ‚Ð°ÐºÐ¾Ð¹ Ð¿Ð°ÐºÐµÑ‚
            // Ð¾Ñ‚ÐºÐ°Ð·Ñ‹Ð²Ð°ÑŽÑ‚ÑÑ Ñ€Ð°Ð·Ð±Ð¸Ñ€Ð°Ñ‚ÑŒ â€” Â«ÐÐµ ÑƒÐ´Ð°Ð»Ð¾ÑÑŒ Ð¾Ð±Ñ€Ð°Ð±Ð¾Ñ‚Ð°Ñ‚ÑŒ Ð¿Ð°ÐºÐµÑ‚Â».
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    androidResources {
        localeFilters += listOf("en", "ru")
    }

    bundle {
        language {
            // Оба языка едут в базовом APK.
            //
            // Play по умолчанию ставит из бандла только язык системы. У нас
            // язык переключается внутри приложения, и на английском телефоне
            // русских строк тогда просто не оказалось бы на устройстве —
            // переключатель показывал бы английский (§9.6).
            enableSplit = false
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
}
