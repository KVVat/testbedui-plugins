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
    //generate key script
    //cd apps/appupdate
    //keytool -genkey -v -keystore mismatched.keystore -alias mismatched -keyalg RSA -keysize 2048 -validity 10000 -storepass password -keypass password -dname "CN=Mismatched, OU=Test, O=CC, L=Tokyo, S=Tokyo, C=JP"
    signingConfigs {
        create("mismatched") {
            storeFile = file("mismatched.keystore")
            storePassword = "password"
            keyAlias = "mismatched"
            keyPassword = "password"
        }
    }
    flavorDimensions.add("version")
    productFlavors {
        create("v1") {
            dimension = "version"
            versionCode = 1
            versionName = "1.0"
        }
        create("v2") {
            dimension = "version"
            versionCode = 2
            versionName = "2.0"
        }
        create("mismatched") {
            dimension = "version"
            versionCode = 1
            versionName = "1.0-mismatched"
            // このフレーバーだけ別の署名設定を当てる
            signingConfig = signingConfigs.getByName("mismatched")
        }
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

tasks.register<Exec>("generateAndDeployTestApks") {
    group = "verification"
    // 各フレーバーのビルドに依存させる
    dependsOn("assembleV1Debug", "assembleV2Debug", "assembleMismatchedDebug", "assembleV1Release")

    val buildDir = layout.buildDirectory.get().asFile.absolutePath
    val targetDir = file("${rootProject.projectDir}/../testbed-core/composeApp/resources").absolutePath
    val scriptPath = file("generate_test_apk.sh").absolutePath

    commandLine("bash", scriptPath, buildDir, targetDir)
}

/*
tasks.register<Exec>("generateAndDeployTestApks") {
    group = "verification"
    description = "Generates multiple test APKs with different signatures/versions and deploys them."

    // assembleDebug が完了していることを前提とする
    dependsOn("assembleDebug", "assembleRelease")

    // スクリプトに渡すパスを確定
    val buildDir = layout.buildDirectory.get().asFile.absolutePath
    val projectPath = projectDir.absolutePath
    val targetDir = file("${rootProject.projectDir}/../testbed-core/composeApp/resources").absolutePath

    // 実行するコマンドの設定
    commandLine("bash", "${projectPath}/generate_test_apks.sh")

    // 引数としてパスを渡す
    args(buildDir, projectPath, targetDir)

    // または環境変数として渡すことも可能
    // environment("GRADLE_BUILD_DIR", buildDir)

    doFirst {
        println(">>> Starting APK transformation script...")
        println(">>> Build Dir: $buildDir")
    }

    doLast {
        println("✅ Multi-APK generation and deployment complete.")
    }
}*/

// 既存の assemble フックを新しいタスクに向ける
tasks.named("assemble") {
    finalizedBy("generateAndDeployTestApks")
}