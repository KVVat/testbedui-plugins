package org.example.plugin.utils

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.Feature
import com.malinskiy.adam.request.adbd.RestartAdbdRequest
import com.malinskiy.adam.request.adbd.RootAdbdMode
import com.malinskiy.adam.request.logcat.ChanneledLogcatRequest
import com.malinskiy.adam.request.misc.FetchHostFeaturesRequest
import com.malinskiy.adam.request.pkg.InstallRemotePackageRequest
import com.malinskiy.adam.request.pkg.UninstallRemotePackageRequest
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.malinskiy.adam.request.shell.v2.ShellCommandResult
import com.malinskiy.adam.request.sync.v2.PullFileRequest
import com.malinskiy.adam.request.sync.v2.PushFileRequest
import kotlinx.coroutines.*
import org.example.project.adb.rules.AdbDeviceRule
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

object AdamUtils {
    data class LogcatResult(val tag: String, val text: String)
    sealed class LogLine {
        data class Log(val datetime: ZonedDateTime, val pid: Int, val tid: Int, val level: Char, val tag: String, val text: String) : LogLine() {}
        class Unparseable(val line: String) : LogLine() {}

        companion object {
            private val LOG_LINE_REGEX = Pattern.compile("([0-9- :.]*?) *(\\d+)-(\\d+)/.*? ([VDIWEF])/(.*?): (.*)")
            private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", Locale.US)

            fun of(line: String, timezone: TimeZone): LogLine {
                val rawLine = line.trim()
                val matcher: Matcher = LOG_LINE_REGEX.matcher(rawLine)
                return if (matcher.matches()) {
                    val date = matcher.group(1).trim()
                    val pid = matcher.group(2).trim().toInt()
                    val tid = matcher.group(3).trim().toInt()
                    val level = matcher.group(4).trim()[0]
                    val tag = matcher.group(5).trim()
                    val text = matcher.group(6).trim()
                    val now = ZonedDateTime.now(timezone.toZoneId())
                    val parsedDate = DATE_TIME_FORMATTER.parse(date)
                    val datetime = now
                        .withHour(parsedDate.get(java.time.temporal.ChronoField.HOUR_OF_DAY))
                        .withMinute(parsedDate.get(java.time.temporal.ChronoField.MINUTE_OF_HOUR))
                        .withSecond(parsedDate.get(java.time.temporal.ChronoField.SECOND_OF_MINUTE))
                        .withNano(parsedDate.get(java.time.temporal.ChronoField.NANO_OF_SECOND))
                    Log(datetime, pid, tid, level, tag, text)
                } else {
                    Unparseable(rawLine)
                }
            }
        }
    }

    fun root(adb: AdbDeviceRule):String{
      var ret:String
      runBlocking {
       ret = adb.adb.execute(
         request = RestartAdbdRequest(RootAdbdMode),
         serial = adb.deviceSerial)
      }

      println("Restart adb=>$ret")
      return ret
    }
    fun shellRequest(shellCommand:String,adb: AdbDeviceRule):ShellCommandResult{
      var ret:ShellCommandResult

      runBlocking {

        ret = adb.adb.execute(
          ShellCommandRequest(shellCommand),
          adb.deviceSerial)
      }
      println("Run shell command(${ret.exitCode}):$shellCommand")

      return ret
    }

    suspend fun installApk(
        client: AndroidDebugBridgeClient,
        serial: String,
        file: File,
        reinstall: Boolean = false
    ): String {
        val remotePath = "/data/local/tmp/${file.name}"
        println("[AdamUtils] Installing ${file.name}...")

        val features = client.execute(FetchHostFeaturesRequest(), serial)
        val channel = client.execute(
            PushFileRequest(file, remotePath, supportedFeatures = features),
            CoroutineScope(Dispatchers.IO),
            serial = serial
        )
        for (progress in channel) {}

        val installCmd = InstallRemotePackageRequest(remotePath, reinstall, listOf("-g"))
        val result = client.execute(installCmd, serial)

        if (result.output.contains("Success")) {
            println("[AdamUtils] Install Success: ${result.output.trim()}")
        } else {
            println("[AdamUtils] Install Failed: ${result.output}")
            throw RuntimeException("Install failed: ${result.output}")
        }
        return result.output
    }

    suspend fun uninstallApk(client: AndroidDebugBridgeClient, serial: String, packageName: String) {
        println("[AdamUtils] Uninstalling $packageName...")
        val result = client.execute(UninstallRemotePackageRequest(packageName), serial)
        println("[AdamUtils] Uninstall Result: ${result.output.trim()}")
    }

    suspend fun waitLogcatLine(waitTime: Int, tagWait: String, adb: AdbDeviceRule): LogcatResult? {
        var result: LogcatResult? = null
        withTimeoutOrNull(waitTime * 100L) {
            val deviceTimezoneString = adb.adb.execute(com.malinskiy.adam.request.prop.GetSinglePropRequest("persist.sys.timezone"), adb.deviceSerial).trim()
            val deviceTimezone = TimeZone.getTimeZone(deviceTimezoneString)
            val request = ChanneledLogcatRequest(since = null, modes = listOf())
            val channel = adb.adb.execute(request, this, adb.deviceSerial)

            for (line in channel) {
                val logLine = LogLine.of(line, deviceTimezone)
                if (logLine is LogLine.Log && logLine.tag == tagWait) {
                    println("matched logcat line found: ${logLine.text}")
                    result = LogcatResult(logLine.tag, logLine.text)
                    channel.cancel()
                    break
                }
            }
        }
        return result
    }


    suspend fun pullFile(sourcePath: String, dest: String, adb: AdbDeviceRule, copytoFile: Boolean = false) {
        coroutineScope {
            val p: Path = Paths.get(sourcePath)
            val destPath: Path = if (copytoFile) Paths.get(dest) else Paths.get(dest, p.fileName.toString())
            val features: List<Feature> = adb.adb.execute(FetchHostFeaturesRequest())
            val channel = adb.adb.execute(
                PullFileRequest(sourcePath, destPath.toFile(), supportedFeatures = features),
                this,
                adb.deviceSerial
            )

            println("Process(Pull):$sourcePath=>$destPath")
            for (percentageDouble in channel) {
                val percentage = (percentageDouble * 100).toInt()
                if (percentage >= 100) {
                    println("Pulling a file($sourcePath) $percentage% done")
                }
            }
        }
    }
}