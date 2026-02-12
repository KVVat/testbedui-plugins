plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    id("com.google.devtools.ksp")
}

android {
    namespace = "org.example.assets.attacker"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.example.assets.attacker"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // In this project, res folder is not used
    sourceSets {
        getByName("main") {
            res.setSrcDirs(listOf("src/main/res"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
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
    rename { "assets-attacker-app.apk" }

    doLast {
        println("✅ APK copied to: ${coreResourcesDir.absolutePath}")
    }
}

// Hook the copy task to run automatically after build
tasks.named("assemble") {
    finalizedBy("copyApkToCore")
}