package org.example.plugin.ftpitc

import org.pcap4j.core.Pcaps
import org.pcap4j.packet.TcpPacket
import org.example.plugin.utils.logi
import java.io.File

class PcapAnalysisSpike {

    fun analyze(pcapFilePath: String) {
        val file = File(pcapFilePath)
        if (!file.exists()) {
            logi("Pcap file NOT FOUND at: ${file.absolutePath}")
            return
        }
        
        val handle = Pcaps.openOffline(pcapFilePath)
        var count = 0
        try {
            while (true) {
                val packet = handle.nextPacket ?: break
                count++
                
                val tcpPacket = packet.get(TcpPacket::class.java) ?: continue
                val payload = tcpPacket.payload ?: continue
                val data = payload.rawData

                if (data.size > 5 && data[0] == 0x16.toByte()) {
                    val handshakeType = data[5]
                    if (handshakeType == 0x01.toByte()) {
                        logi("Found Client Hello at packet #$count")
                        parseClientHello(data)
                    }
                }
            }
        } catch (e: Exception) {
            logi("Error during analysis: ${e.message}")
        } finally {
            handle.close()
        }
    }

    private fun parseClientHello(data: ByteArray) {
        if (data.size < 44) return
        val sessionIdLen = data[43].toInt() and 0xFF
        var offset = 44 + sessionIdLen
        
        if (data.size < offset + 2) return
        val cipherSuitesLen = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        offset += 2
        
        logi("Cipher Suites Count: ${cipherSuitesLen / 2}")
        for (i in 0 until cipherSuitesLen step 2) {
            if (data.size < offset + i + 2) break
            val suite = ((data[offset + i].toInt() and 0xFF) shl 8) or (data[offset + i + 1].toInt() and 0xFF)
            logi("  Cipher Suite: 0x${"%04X".format(suite)}")
        }
    }
}
