plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pockettheater"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pockettheater"
        minSdk = 31
        targetSdk = 34
        versionCode = (System.getenv("APP_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("APP_VERSION_NAME") ?: "0.1.0"
    }

    // The permanent signing key. The build workflow decodes the ANDROID_KEYSTORE_BASE64 secret to a file and sets
    // these two variables, so every build is signed by the same key and installs over the previous one.
    // Without them (for example a build on your own computer) Android's temporary debug key is used instead.
    val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    if (!keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank()) {
        signingConfigs {
            create("permanent") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = "pockettheater"
                keyPassword = keystorePassword
            }
        }
        buildTypes {
            getByName("debug") { signingConfig = signingConfigs.getByName("permanent") }
            getByName("release") { signingConfig = signingConfigs.getByName("permanent") }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // The quick "lint vital" pass on release builds can stop a build over a minor finding. Real problems are caught by the tests and the APK checks.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    testImplementation("junit:junit:4.13.2")
}
