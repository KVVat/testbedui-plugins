package org.example.plugin.utils


import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import org.example.project.JUnitBridge
import java.nio.file.Paths
import java.text.MessageFormat
import java.time.LocalDateTime
import java.util.Properties
import kotlin.io.path.exists
import org.example.project.adb.rules.AdbDeviceRule

class ADSRPTestWatcher(adbDeviceRule: AdbDeviceRule):TestWatcher() {

  private var adbDeviceRule: AdbDeviceRule =adbDeviceRule
  private lateinit var deviceType:String
  private lateinit var deviceSerial:String
  private lateinit var system:String
  private lateinit var osversion:String


  override fun starting(desc: Description?) {
    log(MessageFormat.format("==========================================\n[Test Start] : {0} on {1}", desc, LocalDateTime.now()))
  }
 
  override fun succeeded(desc: Description?) {
    log(MessageFormat.format("[Test Succeeded] : {0}", desc))
  }
 
  override fun failed(e: Throwable, desc: Description?) {
    loge(
      MessageFormat.format(
        "[Test Failed] : {0} \r\n*** Exception : {1}.", desc, e.message
      )
    )
  }


  fun propertiesAddStringToArray(path_:String, key:String, value:String)
  {
    val prop= Properties()
    val path = Paths.get(path_)
    val parent = path.parent
    if (parent != null && !Files.exists(parent)) {
        Files.createDirectories(parent)
    }
    var mValue:String = value
    if(path.exists()){
      FileInputStream(path.toFile()).use { stream->
        try {
          prop.load(stream)
          val values:String = prop.getProperty(key,"")
          val aVal = values.split(",").toMutableList()
          if(!aVal.contains(value)) aVal.add(value)
          mValue = aVal.joinToString(",")

        } catch (e:IOException) {
          println("Prop Read Error:$e")
        } finally {
          stream.close()
        }
        prop.setProperty(key,mValue)
      }
    } else {
      prop.setProperty(key,mValue)
    }
    //println("new value for the $key=>$mValue")
    //Write new value
    FileOutputStream(path.toFile()).use { stream->
      try {
        prop.store(stream,"Props for process.")
      } catch (e:IOException) {
        println("Prop Error:$e")
      } finally {
        stream.close()
      }
    }
  }

  /**
   *
   */
  override fun finished(desc: Description?) {
    println(MessageFormat.format("[Test Finished] : {0}", desc))

    val myClassKClass = desc!!.testClass
    
    var sfr = myClassKClass.getAnnotation(SFR::class.java)
    if(sfr == null){
      sfr = SFR("dummy","dummy")
    }
    println(sfr.title)
    //save_signature_to_tmp_file
    val xmlPatchesDir = File(JUnitBridge.resultsDir, "xml-patches")
    if (!xmlPatchesDir.exists()) {
        xmlPatchesDir.mkdirs()
    }
    
    // Read timestamp from lock file if available
    val lockFile = File(JUnitBridge.resultsDir, "${myClassKClass.simpleName}.lock")
    val timestamp = if (lockFile.exists()) {
        lockFile.readText().trim()
    } else {
        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    }

    val pout = File(xmlPatchesDir, "PATCH-junit-report-${myClassKClass.simpleName}-${timestamp}.xml").toPath()

    /*
  val timestamp =
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        */

    if(pout.exists()){
      Files.delete(pout)
    }
    val fout= File(pout.toUri())

    propertiesAddStringToArray(File(xmlPatchesDir, "patch.prop").absolutePath,
      "classname",myClassKClass.name)

    this.deviceType=adbDeviceRule.productmodel.trim()
    this.deviceSerial=adbDeviceRule.deviceSerial.trim()
    this.osversion=adbDeviceRule.osversion.trim()
    this.system = "" // Workaround for uninitialized property
    val title_ = sfr.title.trim()
    val desc_ = sfr.description.trim()

    val logContent = LocalLog.getAsString()
    val summary_ = SFRCheckList.getSummaryString()
    val diffText = """
<diff>
   <add sel="/testsuite/properties">
       <property name="SFR.name" value="${title_}" />
       <property name="SFR.description" value="${desc_}" />
       <property name="device" value="${deviceType}" />
       <property name="osversion" value="${osversion}" />
       <property name="system" value="${system}" />
       <property name="signature" value="${deviceSerial}" />
       <property name="summary" value="${summary_}" />
   </add>
   <update sel="/testsuite/system-out"><![CDATA[
${logContent}
]]></update>
</diff>
    """

    FileWriter(fout).use{ writer ->
      try {
        writer.write(diffText.trim())
        writer.close()
        println("Xml Patch:Prepared")
      } catch (e:IOException) {
        println("Xml Patch:$e")
      }
    }

    // Merge into target JUnit report
    val reportFile = File(JUnitBridge.resultsDir, "junit-report-${myClassKClass.simpleName}-${timestamp}.xml")
    if (reportFile.exists()) {
        try {
            var content = reportFile.readText()
            
            // 1. Insert properties
            val propsToAdd = """
    <property name="SFR.name" value="${title_}" />
    <property name="SFR.description" value="${desc_}" />
    <property name="device" value="${deviceType}" />
    <property name="osversion" value="${osversion}" />
    <property name="system" value="${system}" />
    <property name="signature" value="${deviceSerial}" />
    <property name="summary" value="${summary_}" />
            """.trimIndent()
            
            content = content.replace("</properties>", "${propsToAdd}\n  </properties>")
            
            // 2. Insert system-out
            content = content.replace("<system-out><![CDATA[]]></system-out>", "<system-out><![CDATA[\n${logContent}\n]]></system-out>")
            
            reportFile.writeText(content)
            println("JUnit Report:Merged")
        } catch (e: Exception) {
            println("JUnit Report Merge Error: ${e.message}")
        }
    } else {
        println("JUnit Report:Not found for merging: ${reportFile.absolutePath}")
    }
  }
}