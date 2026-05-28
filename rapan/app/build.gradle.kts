plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val rapanKeystorePath = System.getenv("RAPAN_KEYSTORE_PATH")
val rapanKeystorePassword = System.getenv("RAPAN_KEYSTORE_PASSWORD")
val rapanKeyAlias = System.getenv("RAPAN_KEY_ALIAS")
val rapanKeyPassword = System.getenv("RAPAN_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    rapanKeystorePath,
    rapanKeystorePassword,
    rapanKeyAlias,
    rapanKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "ru.rapan.miniapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.rapan.miniapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.6"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(rapanKeystorePath!!)
                storePassword = rapanKeystorePassword
                keyAlias = rapanKeyAlias
                keyPassword = rapanKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions += "audience"
    productFlavors {
        create("client") {
            dimension = "audience"
            applicationId = "ru.rapan.client"
            resValue("string", "app_name", "КЛИЕНТ")
            buildConfigField("String", "START_URL", "\"https://xn----7sbab4blsohri.xn--p1ai/index/order\"")
            buildConfigField("String", "BROWSER_TARGET_URL", "\"https://xn----7sbab4blsohri.xn--p1ai/index/order\"")
            buildConfigField("String", "EXPECTED_REDIRECT_PATH", "\"/index/order\"")
            buildConfigField("String", "RETURNING_BROWSER_URL", "\"https://xn----7sbab4blsohri.xn--p1ai/index/order\"")
        }
        create("driver") {
            dimension = "audience"
            applicationId = "ru.rapan.driver"
            resValue("string", "app_name", "РАПАН ТАКСИ")
            buildConfigField("String", "START_URL", "\"https://xn----7sbab4blsohri.xn--p1ai/user/\"")
            buildConfigField("String", "BROWSER_TARGET_URL", "\"https://xn----7sbab4blsohri.xn--p1ai/user/registration\"")
            buildConfigField("String", "EXPECTED_REDIRECT_PATH", "\"/user/registration\"")
            buildConfigField("String", "RETURNING_BROWSER_URL", "\"https://xn----7sbab4blsohri.xn--p1ai/user/\"")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
