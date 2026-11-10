plugins {
    id("com.android.application")
}

android {
    namespace = "com.xcontroller.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xcontroller.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 16
        versionName = "7.2"
    }

    // Har build same key se sign hoti hai, taaki naya version purane ke upar install ho sake.
    signingConfigs {
        create("xkey") {
            storeFile = file("xcontroller.p12")
            storePassword = "xcontroller123"
            keyAlias = "xcontroller"
            keyPassword = "xcontroller123"
            storeType = "PKCS12"
            // purane installers ke liye v1 bhi, aur modern ke liye v2/v3
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("xkey")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("xkey")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
