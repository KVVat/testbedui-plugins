package org.example.plugin.utils

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.pkg.InstallRemotePackageRequest
import com.malinskiy.adam.request.pkg.UninstallRemotePackageRequest
import com.malinskiy.adam.request.sync.v1.PushFileRequest
import com.malinskiy.adam.request.logcat.ChanneledLogcatRequest
import com.malinskiy.adam.request.logcat.LogcatReadMode
import com.malinskiy.adam.request.misc.FetchHostFeaturesRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import java.io.File

object AdamUtils {

    suspend fun installApk(
        client: AndroidDebugBridgeClient,
        serial: String,
        file: File,
        reinstall: Boolean = false
    ) {
        val remotePath = "/data/local/tmp/${file.name}"
        logi("[AdamUtils] Installing ${file.name}...")

        // 1. Push APK
        val pushChannel = client.execute(
            PushFileRequest(file, remotePath),
            GlobalScope,
            serial = serial
        )
        // Wait for push completion
        for (progress in pushChannel) { }

        // 2. Install
        val installCmd = InstallRemotePackageRequest(
            remotePath,
            reinstall = reinstall,
            extraArgs = listOf("-g") // Grant runtime permissions
        )
        val result = client.execute(installCmd, serial)

        if (result.output.contains("Success")) {
            logd("[AdamUtils] Install Success: ${result.output.trim()}")
        } else {
            loge("[AdamUtils] Install Failed: ${result.output}")
            throw RuntimeException("Install failed: ${result.output}")
        }
    }

    suspend fun uninstallApk(
        client: AndroidDebugBridgeClient,
        serial: String,
        packageName: String
    ) {
        logi("[AdamUtils] Uninstalling $packageName...")
        val result = client.execute(UninstallRemotePackageRequest(packageName), serial)
        logd("[AdamUtils] Uninstall Result: ${result.output.trim()}")
    }

    suspend fun waitForLogcat(
        client: AndroidDebugBridgeClient,
        serial: String,
        tag: String,
        msgSubstring: String,
        timeoutMs: Long = 5000
    ): String? {
        logd("[AdamUtils] Waiting for logcat: tag='$tag', msg='$msgSubstring'")
        var foundLine: String? = null

        withTimeoutOrNull(timeoutMs) {
            val request = ChanneledLogcatRequest(modes = listOf(LogcatReadMode.threadtime))
            val channel = client.execute(request, this, serial)

            try {
                channel.consumeEach { chunk ->
                    chunk.split("\n").forEach { line ->
                        if (line.contains(tag) && line.contains(msgSubstring)) {
                            foundLine = line
                            channel.cancel()
                            return@consumeEach
                        }
                    }
                }
            } catch (e: Exception) { /* Channel closed */ }
        }
        return foundLine
    }
}