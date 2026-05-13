plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.example.openurl.niapsec"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.openurl.niapsec"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
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
    rename { "openurl-niapsec-debug.apk" }

    doLast {
        println("✅ APK copied to: ${coreResourcesDir.absolutePath}")
    }
}

// Stage TLS test fixtures (certs / keys / p12 bundles) used by host-side
// tests such as testSessionResumptionWithSServer (cert.pem/key.pem) and
// testMutualAuthentication (badssl.com-client.p12). These are loaded via
// JUnitBridge.resourceDir which resolves to the testbed-core resources dir.
tasks.register<Copy>("copyTestFixturesToCore") {
    description = "Copies TLS test fixtures (certs/keys) to the TestBed Core resources directory."

    from("${projectDir}/resources")
    include("cert.pem", "key.pem", "badssl.com-client.p12", "badssl.com-client.pem", "wildcard-rsa2048.crt")

    val coreResourcesDir = file("${rootProject.projectDir}/../testbed-core/composeApp/resources")
    if (!coreResourcesDir.exists()) {
        coreResourcesDir.mkdirs()
    }
    into(coreResourcesDir)

    doLast {
        println("✅ Test fixtures copied to: ${coreResourcesDir.absolutePath}")
    }
}

// Hook the copy tasks to run automatically after build
tasks.named("assemble") {
    finalizedBy("copyApkToCore", "copyTestFixturesToCore")
}