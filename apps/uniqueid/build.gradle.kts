plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.example.uniqueid"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.uniqueid"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
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
    rename { "uniqueid-debug.apk" }

    doLast {
        println("✅ APK copied to: ${coreResourcesDir.absolutePath}")
    }
}

// Hook the copy task to run automatically after build
tasks.named("assemble") {
    finalizedBy("copyApkToCore")
}