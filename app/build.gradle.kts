plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "fr.xam74er1.trellodrone"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.xam74er1.trellodrone"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8

        buildFeatures {
            viewBinding = true
        }
    }
}

dependencies {
    implementation("org.opencv:opencv:4.9.0")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.3.1")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}