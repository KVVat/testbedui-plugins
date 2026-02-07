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
    // 1. 本体のコンパイル済みクラスを参照 (../.. で testbedui 側へ抜ける)
    // 本体側で ./gradlew :composeApp:compileKotlinJvm が実行済みである必要があります
    implementation(files("../../testbedui/composeApp/build/classes/kotlin/jvm/main"))
    implementation(project(":common-utils"))
    // 2. テスト実行に必要な最小限の依存関係
    implementation("junit:junit:4.13.2")
    implementation("com.malinskiy.adam:adam:0.5.10")
    // 本体の JUnitBridge や AdbDeviceRule が依存している coroutines も必要
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

}

tasks.jar {
    val pluginName = project.name
    archiveFileName.set("$pluginName.jar")

    // common-utils などの依存モジュールを JAR に含める設定
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    // 3. 本体の plugins/配下の個別ディレクトリへ出力
    destinationDirectory.set(file("${rootProject.projectDir}/../testbedui/composeApp/plugins/$pluginName"))
}

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