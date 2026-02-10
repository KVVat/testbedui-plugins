plugins {
    kotlin("jvm")
}
kotlin {
    jvmToolchain(17)
}
repositories {
    google()
    mavenCentral()
}

dependencies {
    // 本体 (testbedui) のクラスを参照
    implementation(files("../../testbed-core/composeApp/build/classes/kotlin/jvm/main"))

    // 共通で使うライブラリ
    implementation("junit:junit:4.13.2")
    implementation("com.malinskiy.adam:adam:0.5.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
}

// Java/Kotlinのターゲット設定を17に統一
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        // 本体側が17なら17、21なら21にする
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}