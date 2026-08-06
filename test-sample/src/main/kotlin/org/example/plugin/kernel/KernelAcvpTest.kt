package org.example.plugin.kernel

import org.example.plugin.utils.*
import org.example.project.adb.rules.AdbDeviceRule
import org.example.project.JUnitBridge
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.flipkart.zjsonpatch.DiffFlags
import com.flipkart.zjsonpatch.JsonDiff
import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import com.malinskiy.adam.request.sync.v1.PushFileRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.hamcrest.core.IsEqual
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumSet
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

@SFR(
  title = "FIPS 140-3 Kernel ACVP Test",
  description = "FIPS 140-3 cryptographic algorithm validation for Android kernel cryptographic modules using ACVP tool.",
  category = "crypto"
)
class KernelAcvpTest {

  @get:Rule
  val adb = AdbDeviceRule()
  private val client: AndroidDebugBridgeClient by lazy { adb.adb }

  @get:Rule
  val watcher: TestWatcher by lazy { ADSRPTestWatcher(adb) }
  @get:Rule
  val errs: ErrorCollector = ErrorCollector()
  @get:Rule
  val name: TestName = TestName()

  // Assert Log
  private val a: TestAssertLogger by lazy { TestAssertLogger(name) }

  @Before
  fun setup() {
    runBlocking {}
  }

  @After
  fun teardown() {
    runBlocking {}
  }

  val RES_PATH = File(JUnitBridge.resourceDir, "kernelacvp").absolutePath
  val OUT_PATH = File(JUnitBridge.resultsDir, "kernelacvp").absolutePath + "/"

  @OptIn(ExperimentalCoroutinesApi::class)
  fun pushFileToTmp(objFile: File, permission: String = "", destdir: String = "/data/local/tmp/") {
    runBlocking {
      val fileName = objFile.name
      val channel = client.execute(
        PushFileRequest(objFile, "$destdir$fileName"),
        GlobalScope,
        serial = adb.deviceSerial
      )

      var done = false
      while (!channel.isClosedForReceive) {
        val progress: Double? =
          channel.tryReceive().onClosed {
            Thread.sleep(1)
          }.getOrNull()
        if (progress != null && progress == 1.0 && !done) {
          logd("Push file $fileName completed")
          done = true
        }
      }

      if (permission.isNotEmpty()) {
        client.execute(
          request = ShellCommandRequest("chmod $permission $destdir$fileName"),
          serial = adb.deviceSerial
        )
      }
    }
  }

  fun batch_install(source_: String, dest_: String, files: Array<String>): Boolean {
    runBlocking {
      logi("Batch installing ${files.size} items from $source_ to $dest_")
      files.forEach {
        var target: String = it
        var mode = "555"
        if (it.contains(":")) {
          val targetarg = it.split(":")
          target = targetarg[0]
          mode = targetarg[1]
        }
        logd("Pushing: ${Paths.get(source_, target).toUri()} => $dest_")
        pushFileToTmp(File(Paths.get(source_, target).toUri()), mode, dest_)
      }
    }
    return true
  }

  fun batch_install(source_: String, dest_: String, files: List<Path>): Boolean {
    runBlocking {
      logi("Batch installing ${files.size} vector files to $dest_")
      files.forEach {
        val target: String = it.fileName.toString()
        val mode = "555"
        pushFileToTmp(File(Paths.get(source_, target).toUri()), mode, dest_)
      }
    }
    return true
  }

  private fun bz2reader(fileURI: URI): String {
    FileInputStream(File(fileURI)).use { fis ->
      BufferedInputStream(fis).use { bis ->
        CompressorStreamFactory().createCompressorInputStream(bis).use { cis ->
          return BufferedReader(InputStreamReader(cis)).readText()
        }
      }
    }
  }

  private fun targz_reader(fileURI: URI, callback: (String, String) -> Unit) {
    try {
      FileInputStream(File(fileURI)).use { fin ->
        GzipCompressorInputStream(fin).use { gzin ->
          TarArchiveInputStream(gzin).use { tar ->
            var breakLoop = false
            while (!breakLoop) {
              try {
                val entry = tar.nextEntry ?: break
                if (entry.isDirectory) continue
                if (!tar.canReadEntryData(entry)) {
                  breakLoop = true
                } else {
                  val br = BufferedReader(InputStreamReader(tar))
                  callback(entry.name, br.readText())
                }
              } catch (ex: IOException) {
                loge("Failed to read tar entry: ${ex.message}")
                break
              }
            }
          }
        }
      }
    } catch (ex: Exception) {
      loge("Failed to read archive $fileURI: ${ex.message}")
    }
  }

  private fun String.truncate(maxLength: Int = 100): String {
    return if (length > maxLength) take(maxLength) + "..." else this
  }

  private fun findExpectedFile(expectedDir: File, actualFileName: String): File? {
    // 1. Exact match with .bz2 or as-is
    var candidate = File(expectedDir, "$actualFileName.bz2")
    if (candidate.exists()) return candidate

    candidate = File(expectedDir, actualFileName)
    if (candidate.exists()) return candidate

    // 2. Stripped prefix/suffix (e.g. remove -request.json, -request, .json)
    val stripped = actualFileName
      .replace("-request.json", "")
      .replace("-request", "")
      .replace(".json", "")
    candidate = File(expectedDir, "$stripped.bz2")
    if (candidate.exists()) return candidate

    candidate = File(expectedDir, "$stripped-expected.json.bz2")
    if (candidate.exists()) return candidate

    candidate = File(expectedDir, "$stripped-response.json.bz2")
    if (candidate.exists()) return candidate

    // 3. Algorithm-based matching fallback
    val expectedFiles = expectedDir.listFiles() ?: return null
    val algoTokens = listOf(
      "ACVP-AES-CBC-CS3", "ACVP-AES-CBC", "ACVP-AES-CTR", "ACVP-AES-ECB", "ACVP-AES-XTS", "ACVP-AES-GCM",
      "CMAC-AES", "hmacDRBG",
      "HMAC-SHA2-224", "HMAC-SHA2-256", "HMAC-SHA2-384", "HMAC-SHA2-512", "HMAC-SHA-1",
      "SHA2-224", "SHA2-256", "SHA2-384", "SHA2-512", "SHA-1"
    )
    for (token in algoTokens) {
      if (actualFileName.contains(token, ignoreCase = true)) {
        val matched = expectedFiles.firstOrNull { it.name.contains(token, ignoreCase = true) }
        if (matched != null) {
          logd("Matched expected file by algorithm ($token): ${matched.name} for $actualFileName")
          return matched
        }
      }
    }

    return null
  }

  private fun evaluateResultFiles(expectedDirName: String) {
    logi("Evaluating test results against expected files in $expectedDirName...")
    val actualTar = File(OUT_PATH, "actual.tar.gz")
    if (!actualTar.exists()) {
      loge("Actual results archive not found at: ${actualTar.absolutePath}")
      errs.checkThat(a.msg("Actual results archive exists"), false, IsEqual(true))
      return
    }

    val expectedDirFile = File(RES_PATH, expectedDirName.trim('/', '\\'))
    if (!expectedDirFile.exists()) {
      loge("Expected directory not found at: ${expectedDirFile.absolutePath}")
      errs.checkThat(a.msg("Expected directory exists"), false, IsEqual(true))
      return
    }

    targz_reader(actualTar.toURI()) { name, tartext ->
      val fname: String = Paths.get(name).fileName.toString()
      var result = true

      if (tartext.isBlank()) {
        logw("Actual output for $fname is empty (execution failed or timed out), skipping comparison")
        errs.checkThat(a.msg("Evaluate $fname (output not empty)"), false, IsEqual(true))
        return@targz_reader
      }

      val expectedFile = findExpectedFile(expectedDirFile, fname)
      if (expectedFile == null || !expectedFile.exists()) {
        loge("Error: No matching expected file found for $fname in ${expectedDirFile.absolutePath}")
        errs.checkThat(a.msg("Expected file exists for $fname"), false, IsEqual(true))
        return@targz_reader
      }

      try {
        val br2text = if (expectedFile.name.endsWith(".bz2")) {
          bz2reader(expectedFile.toURI())
        } else {
          expectedFile.readText()
        }
        val br2text_ = br2text.lowercase()
        val tartext_ = tartext.lowercase()
        val jsonB: JsonNode = jacksonObjectMapper().readTree(br2text_) // expected
        val jsonT: JsonNode = jacksonObjectMapper().readTree(tartext_) // actual

        val flags: EnumSet<DiffFlags> = DiffFlags.dontNormalizeOpIntoMoveAndCopy().clone()
        val patch: JsonNode = JsonDiff.asJson(jsonT, jsonB, flags)
        val ignoreList = listOf("/1/revision", "/1/issample")

        if (patch.isArray) {
          patch.forEach { jsonNode ->
            val nodepath = jsonNode.get("path")?.textValue() ?: ""
            if (!ignoreList.contains(nodepath)) {
              val resp = jsonNode.toString().truncate(200)
              loge("Found mismatch in $fname (vs ${expectedFile.name}): $resp")
              result = false
            }
          }
        } else {
          logw("Diff patch is blank for $fname")
          result = false
        }

        if (result) {
          logp("Vector verification PASSED: $fname (matched with ${expectedFile.name})")
        }
      } catch (ex: IOException) {
        loge("Error reading expected file for $fname (${expectedFile.name}): ${ex.message}")
        result = false
      } catch (ex: Exception) {
        loge("Processing error during evaluation of $fname: ${ex.message}")
        result = false
      }
      errs.checkThat(a.msg("Evaluate $fname"), result, IsEqual(true))
    }
  }

  @Test
  fun testKernelAcvp() {
    runBlocking {
      val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm XXX")
      logi("==================================================")
      logi("Starting Kernel ACVP (FIPS 140-3) Verification Test")
      logi("==================================================")

      // Step 1: ADB root
      logi("Step 1: Requesting ADB root permission...")
      val ret = AdamUtils.root(adb)
      logi("ADB root result: $ret")
      Thread.sleep(5000)

      // Step 2: Install binaries and configs
      logi("Step 2: Pushing ACVP test harness binaries and configs to device (/data/local/tmp/)...")
      batch_install(
        RES_PATH, "/data/local/tmp/", arrayOf(
          "acvptool:775", "acvp_kernel_harness_arm64:775", "af_alg_config.txt",
          "config.json"
        )
      )

      val vectorsDir = "vectors"
      val expectedDir = "expected"

      // Step 3: Install test vectors
      val vectorDirPath = File(RES_PATH, vectorsDir).toPath()
      if (!vectorDirPath.toFile().exists()) {
        val msg = "Vectors directory not found at: ${vectorDirPath.toAbsolutePath()}"
        loge(msg)
        errs.checkThat(a.msg("Vector directory exists"), false, IsEqual(true))
        return@runBlocking
      }

      val fnames2 = vectorDirPath.listDirectoryEntries("*.bz2")
      logi("Step 3: Found ${fnames2.size} test vector files in $vectorsDir")
      AdamUtils.shellRequest("rm -rf /data/local/tmp/vectors; mkdir -p /data/local/tmp/vectors/", adb)
      batch_install(File(RES_PATH, vectorsDir).absolutePath, "/data/local/tmp/vectors/", fnames2)

      // Step 4: System configuration
      logi("Step 4: Configuring sysctl net.core.optmem_max=204800...")
      val sysctlRes = AdamUtils.shellRequest("sysctl -w net.core.optmem_max=204800", adb)
      logd("sysctl exitCode: ${sysctlRes.exitCode}")

      logi("Step 5: Extracting test vectors on device...")
      AdamUtils.shellRequest("bzip2 -dfk /data/local/tmp/vectors/*.bz2", adb)
      AdamUtils.shellRequest("cd /data/local/tmp/; rm -rf actual; mkdir -p actual", adb)

      // Step 6: Execute ACVP test for each vector
      logi("Step 6: Executing ACVP tests for ${fnames2.size} algorithm vector sets...")
      fnames2.forEachIndexed { idx, it ->
        val fname = it.fileName.nameWithoutExtension
        logi("[${idx + 1}/${fnames2.size}] Processing vector: $fname...")

        // Use timeout (30s) to prevent hanging on unsupported socket operations
        val sr = AdamUtils.shellRequest(
          "cd /data/local/tmp/; timeout 30 ./acvptool -json vectors/$fname -wrapper ./acvp_kernel_harness_arm64 > actual/$fname",
          adb
        )

        val stdoutStr = String(sr.stdout, Charsets.UTF_8).trim()
        val stderrStr = String(sr.stderr, Charsets.UTF_8).trim()
        val outputDetail = buildString {
          if (stdoutStr.isNotEmpty()) append(" stdout: $stdoutStr")
          if (stderrStr.isNotEmpty()) append(" stderr: $stderrStr")
        }

        val line: String
        if (sr.exitCode != 0) {
          line = "\"${dateFormat.format(Date())} *** processing $fname ... failure (exitCode=${sr.exitCode}) ***\"$outputDetail"
          loge(line)
        } else {
          line = "\"${dateFormat.format(Date())} *** processing $fname ... ok ***\"$outputDetail"
          logi(line)
        }

        errs.checkThat(a.msg("Execute acvptool $fname"), sr.exitCode, IsEqual(0))
        AdamUtils.shellRequest("cd /data/local/tmp/; echo \"$line\" >> acvptest.log", adb)
      }

      // Step 7: Pull results
      logi("Step 7: Archiving actual test results on device...")
      AdamUtils.shellRequest("cd /data/local/tmp/; tar -zcvf actual.tar.gz actual", adb)

      logi("Step 8: Pulling test output from device to $OUT_PATH...")
      File(OUT_PATH).mkdirs()
      AdamUtils.pullFile("/data/local/tmp/actual.tar.gz", OUT_PATH, adb)

      // Step 9: Evaluate against expected values
      logi("Step 9: Evaluating result files against expected datasets...")
      evaluateResultFiles(expectedDir)
      logi("=== Kernel ACVP Verification Completed ===")
    }
  }
}
