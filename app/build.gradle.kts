plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("VEBALIST_KEYSTORE_PATH")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("VEBALIST_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VEBALIST_KEY_ALIAS")
                keyPassword = System.getenv("VEBALIST_KEY_PASSWORD")
            }
        }
    }
    namespace = "com.vcorp.vebalist"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vcorp.vebalist"
        minSdk = 26
        targetSdk = 35
        versionCode = 202
        versionName = "2.0.2"

        val backendUrl = (System.getenv("VEBALIST_BACKEND_URL")
            ?: "https://vebalist-backend-v4hce575va-ue.a.run.app")
            .trim()
            .trimEnd('/')
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!System.getenv("VEBALIST_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
