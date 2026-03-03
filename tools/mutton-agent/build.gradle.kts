import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.zip.ZipFile
import java.io.FileOutputStream

plugins {
    id("com.android.library")
    //id(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.example.mutton"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

// R8コンパイラ用設定
val r8Configuration by configurations.creating

dependencies {
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test:monitor:1.7.2")
    androidTestImplementation("org.json:json:20231013")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.1")
}

// --- Agent APK デプロイタスク ---

val copyTestApk = tasks.register("copyTestApk", Copy::class) {
    group = "build"
    description = "Copies the AndroidTest APK to testbed-core resources"

    dependsOn("assembleDebugAndroidTest")

    val buildDir = layout.buildDirectory
    val apkFile = buildDir.file("outputs/apk/androidTest/debug/mutton-agent-debug-androidTest.apk")

    val deployTargetDir = rootProject.projectDir.resolve("../testbed-core/composeApp/resources")

    from(apkFile)
    into(deployTargetDir)
    rename { "mutton-agent.apk" }
}