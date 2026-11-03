import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(localPropsFile.inputStream())
}

// CI run number, so every pushed build is a newer version than the one installed
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1

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

        buildConfigField("String", "GEMINI_API_KEY",
            "\"${localProps.getProperty("GEMINI_API_KEY") ?: ""}\"")

        buildConfigField("String", "CLAUDE_API_KEY",
            "\"${localProps.getProperty("CLAUDE_API_KEY") ?: ""}\"")
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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}