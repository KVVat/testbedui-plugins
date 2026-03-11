package org.example.plugin.ftpitc

import org.junit.Test
import java.io.File

class PcapAnalysisSpikeTest {

    @Test
    fun testAnalyzePcap() {
        // Use absolute path to ensure the file is found in the plugin environment
        val pcapFile = File("/Users/kwatanabe/work-repo/testbedui-plugins/test.pcap")
        
        val spike = PcapAnalysisSpike()
        spike.analyze(pcapFile.absolutePath)
    }
}
