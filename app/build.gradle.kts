plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

// CI run number, so every pushed build is a newer version than the one installed
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1

// Real ads only in builds made with -PliveAds (the Play Store upload); every other build, including
// the CI APK on your own phone, gets Google's test ads so tapping them can't get AdMob banned
val rewardedAdUnit = if (project.hasProperty("liveAds")) "ca-app-pub-7562431805276354/5397079056"
    else "ca-app-pub-3940256099942544/5224354917"

android {
    namespace = "com.example.obliviondescent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.obliviondescent"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "REWARDED_AD_UNIT", "\"$rewardedAdUnit\"")
    }

    signingConfigs {
        getByName("debug") {
            // CI places the shared key here; local builds keep the default debug key
            val ciKeystore = rootProject.file("ci-debug.keystore")
            if (ciKeystore.exists()) storeFile = ciKeystore
        }
    }

    buildTypes {
        release {
            // Strip unused code and resources so each update download is much smaller
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Same key as earlier builds, so the installed game accepts this as an update
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("com.google.android.material:material:1.13.0")
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-analytics")
    implementation(libs.firebase.database)
    implementation(libs.gridlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.gms:play-services-ads:24.9.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
}