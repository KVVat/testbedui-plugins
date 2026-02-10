package com.example.test_suites.utils


import com.example.test_suites.rule.AdbDeviceRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.text.MessageFormat
import java.time.LocalDateTime
import java.util.Properties
import kotlin.io.path.exists


class ADSRPTestWatcher(adbDeviceRule: AdbDeviceRule):TestWatcher() {

  private var adbDeviceRule: AdbDeviceRule =adbDeviceRule
  private lateinit var deviceType:String
  private lateinit var deviceSerial:String
  private lateinit var system:String
  private lateinit var osversion:String


  override fun starting(desc: Description?) {
    println(MessageFormat.format("==========================================\n[Test Start] : {0} on {1}", desc, LocalDateTime.now()))
  }

  override fun succeeded(desc: Description?) {
    println(MessageFormat.format("[Test Succeeded] : {0}", desc))
  }

  override fun failed(e: Throwable, desc: Description?) {
    System.err.println(
      MessageFormat.format(
        "[Test Failed] : {0} \r\n*** Exception : {1}.", desc, e.message
      )
    )
  }

  //private fun getFileFromPath(obj: Any, fileName: String): File? {
    //val classLoader = obj.javaClass.classLoader
    //val resource = classLoader.getResource(fileName)
    //return File(fileName)
  //}


  fun propertiesAddStringToArray(path_:String, key:String, value:String)
  {
    val prop= Properties()
    val path = Paths.get(path_)
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
    //myClassKClass.
    var sfr = myClassKClass.getAnnotation(SFR::class.java)
    if(sfr == null){
      sfr = SFR("dummy","dummy")
    }
    println(sfr.title)
    //save_signature_to_tmp_file
    val pout = Paths.get("../xml-patches/PATCH-${myClassKClass.name}.xml")

    if(pout.exists()){
      Files.delete(pout)
    }
    val fout= File(pout.toUri())

    propertiesAddStringToArray("../xml-patches/patch.prop",
      "classname",myClassKClass.name)

    this.deviceType=adbDeviceRule.productmodel.trim()
    this.deviceSerial=adbDeviceRule.deviceSerial.trim()
    this.osversion=adbDeviceRule.osversion.trim()
    this.system=adbDeviceRule.system.trim()
    val title_ = sfr.title.trim()
    val desc_ = sfr.description.trim()

    val diffText = """
<diff>
   <add sel="/testsuite/properties">
       <property name="SFR.name" value="${title_}" />
       <property name="SFR.description" value="${desc_}" />
       <property name="device" value="${deviceType}" />
       <property name="osversion" value="${osversion}" />
       <property name="system" value="${system}" />
       <property name="signature" value="${deviceSerial}" />
   </add>
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
    //
  }
}