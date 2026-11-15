plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "net.spotlive.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.spotlive.player"
        // SVILUPPO-20261114-02: minSdk per flavor (standard 24, legacy 22). versionCode/versionName invariati.
        targetSdk = 35
        versionCode = 16
        versionName = "1.0.15"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // URL del player web ospitato. 1.0.11: predefinito = produzione (mai più ponte demo per build senza -PurlPlayer).
        // Si cambia senza toccare il codice: ./gradlew assembleStandardDebug -PurlPlayer=https://…/player/
        buildConfigField(
            "String",
            "URL_PLAYER",
            "\"${(project.findProperty("urlPlayer") as String?)?.takeIf { it.startsWith("https://") } ?: "https://209-227-230-76.sslip.io/player/"}\"",
        )
    }

    // SVILUPPO-20261114-02: due varianti dello stesso pacchetto, stessa firma, stesso versionCode.
    //   standard: T30 e tablet (Android 7+), sorgente e comportamento della 1.0.15.
    //   legacy:   Taurus T3 (Android 5.1, API 22): archivio, fiducia TLS e aggiornamenti propri (docs/MODIFICHE-T3.md).
    // Comandi: ./gradlew assembleStandardRelease / assembleLegacyRelease → app/build/outputs/apk/{standard,legacy}/release/
    flavorDimensions += "piattaforma"
    productFlavors {
        create("standard") {
            dimension = "piattaforma"
            minSdk = 24
            buildConfigField("String", "VARIANTE", "\"standard\"")
            buildConfigField("int", "WEBVIEW_CHROME_MIN", "0")
            buildConfigField("String", "VERSIONE_PLAYER", "\"1.0.15\"")
        }
        create("legacy") {
            dimension = "piattaforma"
            minSdk = 22
            versionNameSuffix = "-legacy"
            buildConfigField("String", "VARIANTE", "\"legacy\"")
            // Chrome minimo della WebView per il player web (moduli ES): da confermare sul target del bundle web.
            buildConfigField("int", "WEBVIEW_CHROME_MIN", "61")
            buildConfigField("String", "VERSIONE_PLAYER", "\"1.0.15-legacy\"")
        }
    }

    // Firma di rilascio: SOLO da variabili d'ambiente, niente segreti nel repository.
    //   SPOTLIVE_SIGNING_KEYSTORE  percorso del file .p12 (fuori dal repository)
    //   SPOTLIVE_SIGNING_PASSWORD  password di store e chiave (mai in file o log)
    //   SPOTLIVE_SIGNING_ALIAS     facoltativo, predefinito "spotlive-release"
    val firmaFile = System.getenv("SPOTLIVE_SIGNING_KEYSTORE")
    val firmaPassword = System.getenv("SPOTLIVE_SIGNING_PASSWORD")
    signingConfigs {
        if (!firmaFile.isNullOrBlank() && !firmaPassword.isNullOrBlank()) {
            create("rilascio") {
                storeFile = file(firmaFile)
                storeType = "PKCS12"
                storePassword = firmaPassword
                keyAlias = System.getenv("SPOTLIVE_SIGNING_ALIAS") ?: "spotlive-release"
                keyPassword = firmaPassword
                enableV1Signing = true // indispensabile per la legacy: Android 5.1 verifica solo la v1
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Senza variabili d'ambiente la release resta NON firmata (non installabile): voluto.
            signingConfigs.findByName("rilascio")?.let { signingConfig = it }
            // Codice identico alla debug già provata sui tablet (niente offuscamento).
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
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
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
