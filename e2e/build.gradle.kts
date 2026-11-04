plugins {
    alias(libs.plugins.android.test)
}

/**
 * Сквозные тесты на эмуляторе. Они ставят ту же сборку приложения, что
 * уходит в Play (R8 и всё остальное), и нажимают по экранам, как человек,
 * через UiAutomator. Код приложения тестам не виден: ошибки R8, которых не
 * поймать юнит-тестами, проявляются здесь.
 */
android {
    namespace = "eu.bolh.e2e"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    // Тесты живут в своём процессе и не делят классы с приложением:
    // переименования R8 им не мешают.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("e2e") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "e2e" }
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
