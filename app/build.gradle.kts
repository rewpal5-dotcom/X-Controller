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
        versionCode = 3
        versionName = "2.1"
    }

    // Har build same key se sign hoti hai, taaki naya version purane ke upar install ho sake.
    signingConfigs {
        create("xkey") {
            storeFile = file("xcontroller.p12")
            storePassword = "xcontroller123"
            keyAlias = "xcontroller"
            keyPassword = "xcontroller123"
            storeType = "PKCS12"
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
