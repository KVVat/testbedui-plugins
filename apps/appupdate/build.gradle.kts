plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.example.appupdate"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.appupdate"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlin {
        jvmToolchain(8)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}

// --- Auto-Copy APK to TestBed Core Resources ---

tasks.register<Copy>("copyApkToCore") {
    description = "Copies the generated APK to the TestBed Core resources directory."

    // Source: The output of the assembleDebug task
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    include("*-debug.apk")

    // Destination: ../testbed-core/resources/
    // Assuming 'testbed-core' is a sibling of the root project
    val coreResourcesDir = file("${rootProject.projectDir}/../testbed-core/composeApp/resources")
    if (!coreResourcesDir.exists()) {
        coreResourcesDir.mkdirs()
    }
    into(coreResourcesDir)

    // Optional: Rename for simpler access in tests (e.g., removes version suffix if needed)
    rename { "appupdate.apk" }

    doLast {
        println("✅ APK copied to: ${coreResourcesDir.absolutePath}")
    }
}

// Hook the copy task to run automatically after build
tasks.named("assemble") {
    finalizedBy("copyApkToCore")
}