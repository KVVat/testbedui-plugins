import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.zip.ZipFile
import java.io.FileOutputStream

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
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
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.test:monitor:1.7.2")
    implementation("org.json:json:20231013")

    // R8 (Kotlin 2.x 対応のため 9.0系推奨)
    r8Configuration("com.android.tools:r8:9.0.32")
}

// --- Agent DEX生成 & デプロイタスク (Config Cache対応) ---

val makeAgentJar = tasks.register("makeAgentJar", Exec::class) {
    group = "build"
    description = "Builds the executable dex jar using specific R8 version"

    dependsOn("bundleDebugAar")

    // --- 【設定フェーズ】 ---

    // 1. Android Jarパス (Stringとして確定)
    val androidExt = project.extensions.getByType(com.android.build.gradle.BaseExtension::class)
    val sdkDir = androidExt.sdkDirectory
    val androidJarPath = "${sdkDir.absolutePath}/platforms/android-${androidExt.compileSdkVersion?.removePrefix("android-") ?: "34"}/android.jar"

    // 2. 入力ファイル (【重要】Configurationそのものではなく、FileCollectionとして取得)
    // project.files(...) でラップすることで、Cache可能なファイルセットになります。
    val r8FileCollection = project.files(r8Configuration)
    val runtimeFileCollection = project.files(project.configurations.getByName("debugRuntimeClasspath"))

    // 3. 入出力パス (Provider/Fileとして保持)
    val buildDir = layout.buildDirectory
    val aarDirProp = buildDir.dir("outputs/aar")
    val outputJarProp = buildDir.file("outputs/mutton-agent.jar")

    // 4. デプロイ先 (Fileとして確定)
    val deployTargetFile = rootProject.projectDir
        .resolve("../testbed-core/composeApp/resources/mutton-agent.jar")

    // Inputs/Outputs の宣言 (キャッシュ用)
    inputs.files(r8FileCollection)
    inputs.files(runtimeFileCollection)
    inputs.dir(aarDirProp)
    outputs.file(outputJarProp)

    // --- 【実行フェーズ】 (doFirst) ---
    // ここでは project.* や Configuration オブジェクトには一切触れず、
    // 上記で定義した FileCollection や File 変数のみを使用します。

    doFirst {
        val outputJar = outputJarProp.get().asFile
        val aarDir = aarDirProp.get().asFile

        // 1. AARファイルの特定
        val aarFile = aarDir.listFiles()?.firstOrNull { it.name.endsWith("-debug.aar") }
            ?: throw GradleException("Could not find debug AAR in $aarDir")

        println(">>> Found AAR: ${aarFile.name}")

        // 2. classes.jar の抽出
        val extractedDir = buildDir.get().asFile.resolve("tmp/d8_input")
        extractedDir.mkdirs()
        val classesJar = extractedDir.resolve("classes.jar")

        ZipFile(aarFile).use { zip ->
            val entry = zip.getEntry("classes.jar")
                ?: throw GradleException("classes.jar not found inside AAR")

            zip.getInputStream(entry).use { input ->
                FileOutputStream(classesJar).use { output ->
                    input.copyTo(output)
                }
            }
        }

        // 3. D8 コマンドの構築
        // FileCollectionからファイルを取り出す
        val r8Jar = r8FileCollection.singleFile
        val inputJars = mutableListOf<String>()

        // 自分自身のクラス
        inputJars.add(classesJar.absolutePath)
        // 依存ライブラリ (FileCollectionを展開)
        runtimeFileCollection.files.filter { it.name.endsWith(".jar") }.forEach {
            inputJars.add(it.absolutePath)
        }

        println(">>> d8 inputs: ${inputJars.size} jars")

        // コマンド設定
        commandLine(
            "java",
            "-cp", r8Jar.absolutePath,
            "com.android.tools.r8.D8",
            "--output", outputJar.absolutePath,
            "--lib", androidJarPath,
            "--min-api", "26"
        )
        args(inputJars)
    }

    doLast {
        // デプロイ処理
        val builtJar = outputJarProp.get().asFile

        if (builtJar.exists()) {
            println(">>> Dexing complete: ${builtJar.name}")

            if (!deployTargetFile.parentFile.exists()) {
                deployTargetFile.parentFile.mkdirs()
            }
            builtJar.copyTo(deployTargetFile, overwrite = true)
            println(">>> Deployed to: ${deployTargetFile.absolutePath}")
        } else {
            throw GradleException("Error: Agent jar was not created.")
        }
    }
}