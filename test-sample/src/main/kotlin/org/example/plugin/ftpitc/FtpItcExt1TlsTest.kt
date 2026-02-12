package org.example.plugin.ftpitc

import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.AdamUtils
import org.example.plugin.utils.HostShellHelper
import org.example.plugin.utils.SFR
import org.example.plugin.utils.TestAssertLogger
import org.example.plugin.utils.UIAutomatorSession
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.runBlocking
import org.dom4j.Document
import org.dom4j.Element
import org.dom4j.Node
import org.dom4j.io.SAXReader
import org.hamcrest.core.Is
import org.hamcrest.core.IsEqual
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.absolutePathString
import kotlin.system.exitProcess
import org.example.project.adb.rules.AdbDeviceRule
import com.malinskiy.adam.AndroidDebugBridgeClient

@SFR("FTP_ITC_EXT.1/TLS", """
FTP_ITC_EXT.1/TLS
The TSF shall provide a communication channel between itself and another trusted 
IT product that is logically distinct from other communication channels and provides 
assured identification of its end points and protection of the channel data from 
modification or disclosure.
If TLS is supported by the TOE, the TLS channel shall as a minimum:
 implement TLS v1.2 [7], TLS v1.3 [11] or higher version of TLS; 
 and support X.509v3 certificates for mutual(cross) authentication;
 and determine validity of the peer certificate by certificate path, 
 expiration date and revocation status according to IETF RFC 5280 [8]; and./client/build/install/client/bin/hello-world-client
notify the TSF and [selection: not establish the connection, 
request application authorization to establish the connection, no other action] 
if the peer certificate is deemed invalid; 
 and support one of the following ciphersuites: ...
  """)
class FtpItcExt1TlsTest {

  //private val TEST_PACKAGE = "com.example.networkcheck"
  private val TEST_MODULE = "networkcheck-debug.apk"

  @get:Rule
  val adb = AdbDeviceRule()
  private val client: AndroidDebugBridgeClient by lazy { adb.adb }

  @get:Rule
  val watcher: TestWatcher by lazy { ADSRPTestWatcher(adb) }
  @get:Rule
  val testname: TestName = TestName()

  //Asset Log
  private val a: TestAssertLogger by lazy { TestAssertLogger(testname) }
  @get:Rule
  val errs: ErrorCollector = ErrorCollector()

  var PKG_PCAPDROID ="com.emanuelef.remote_capture"

  val REQUIRED_CIPHERS_IN_SFR = arrayOf(
    "TLS_RSA_WITH_AES_256_GCM_SHA384",
    "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
    "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
    "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
    "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
    "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384")

  @Before
  fun setup() {
    runBlocking {
      //client.execute(UninstallRemotePackageRequest(PKG_PCAPDROID), adb.deviceSerial)
      client.execute(ShellCommandRequest("rm /data/local/tmp/$TEST_MODULE"),
                     adb.deviceSerial)

    }
  }

  @After
  fun teardown() {
    runBlocking {
      client.execute(ShellCommandRequest("rm /data/local/tmp/$TEST_MODULE"),
                     adb.deviceSerial)
    }
  }

  fun Node.attrib(attrib:String="showname"):String{
    val elem = this as Element
    return elem.attributeValue(attrib).toString()
  }
  fun Node.selectChild(key:String):Node?
  {
    //if(this == null) return null;
    return this.selectSingleNode(".//descendant::field[@name='${key}']")
  }
  fun Node.selectChildren(key:String):List<Node>?
  {
    //if(this == null) return null;
    return this.selectNodes(".//descendant::field[@name='${key}']")
  }
  fun Node.packetSerial():Int
  {
    return _value(this.selectSingleNode(
      ".//proto[@name='geninfo']/field[@name='num']")).toInt(16)
  }

  fun _showname(n:Node?):String{
    return n?.attrib() ?: "N/A"
  }
  fun _show(n:Node?):String{
    return n?.attrib("show") ?: "N/A"
  }
  fun _value(n:Node?):String {
    return n?.attrib("value") ?: "0"
  }

  @Test
  fun testNormalHost(){
    if(isLockScreenEnbled()){
      println("lock screen is enabled. please unlock")
      assert(false)
      return
    }

    val hostName = "https://tls-v1-2.badssl.com:1012/"
    val resp:Pair<String,Path> =
      tlsCapturePacket("normal",hostName)
    val httpret:String = resp.first
    errs.checkThat(httpret, IsEqual( "200"))
    println(resp)

    val pdml_path  = resp.second.absolutePathString()
    anaylzeCertainPdml(Paths.get(pdml_path+".xml"),hostName)
  }

  @Test
  fun testExpiredHost(){
    if(isLockScreenEnbled()){
      println("lock screen is enabled. please unlock")
      assert(false)
      return
    }
    val hostName = "https://expired.badssl.com/"
    val resp:Pair<String,Path> =
      tlsCapturePacket("expired",hostName)
    val httpret:String = resp.first
    errs.checkThat(httpret, IsEqual( "525"))
    //println(resp)

    val pdml_path  = resp.second.absolutePathString()
    anaylzeCertainPdml(Paths.get(pdml_path+".xml"),hostName)
  }

  @Test
  fun testInvalidHost(){
    if(isLockScreenEnbled()){
      println("lock screen is enabled. please unlock")
      assert(false)
      return
    }
    val hostName = "https://wrong.host.badssl.com/"
    val resp:Pair<String,Path> =
      tlsCapturePacket("expired",hostName)
    val httpret:String = resp.first
    errs.checkThat(httpret, IsEqual( "526"))
    //println(resp)

    val pdml_path  = resp.second.absolutePathString()
    anaylzeCertainPdml(Paths.get(pdml_path+".xml"),hostName)
  }

  fun anaylzeCertainPdml(p:Path, targetHost:String)
  {
    //var p:Path = Paths.get("../results/capture/20230615140545-expired.pcap.xml")
    //var targetHost = "https://expired.badssl.com"
    val document:Document = SAXReader().read(File(p.toUri()))
    //Start DNS record check
    //determine entry point of the analyze with dnspackets
    //we should verify the tlspackets after dns query to the target host...
    val dnspkts = document.selectNodes("/pdml/packet/proto[@name='dns']")
    if(dnspkts.size == 0) {
      //there are no dns records...exit
      //errs.checkThat(a.Msg("Need at least one dns packet in cocument"), dnspkts.size,)
      println("need at least one dns packet to continue")
      Assert.assertTrue(false)
      return
    }

    var readAfter = 0
    for(pkt in dnspkts){
      val num = pkt.parent.packetSerial()
      val queryname = _show(pkt.selectChild("dns.qry.name"))
      if(targetHost.contains(queryname)){
        println("Target : $targetHost contains $queryname. we'll examine after this($num) packet.")
        readAfter = num
        break
      }
    }

    //Start TLS record check
    val nodes = document.selectNodes("/pdml/packet/proto[@name='tls']")
    if(nodes.size == 0) {
      //there are no tls records...exit
      println("need at least one tls packet to continue")
      Assert.assertTrue(false)
      return
    }

    var helloLookupDone = false
    var certLookupDone = false
    var certExpire = false
    var certProblemFound = false

    for(tlsp in nodes){
      val records = tlsp.selectChildren("tls.record")//multiple tls.records can be exist in a proto tag
      val serial = tlsp.parent.packetSerial()
      if(serial<readAfter){
        //println("Packet Number:$serial < $readAfter.")
        continue
      } else {
        println("Packet Number=$serial")
      }
      if(records !== null) {
        var i=1
        for (record in records) {
          println(record.attrib()+"[$serial-$i]")
          println("\t" + _showname(record.selectChild("tls.record.version")))
          println("\t" + _showname(record.selectChild("tls.handshake.type")))
          println("\t" + _showname(record.selectChild("tls.record.content_type")))
          //tls.record.content_type
          //println("\t\t>" + _value(record.selectChild("tls.handshake.type")))
          val hsType = _value(record.selectChild("tls.handshake.type")).toInt(16)
          val cnType = _value(record.selectChild("tls.record.content_type")).toInt(16)
          if(hsType>0){
            //test for client hello
            if(hsType == 1 && !helloLookupDone){ //Client Hello
              //test 1: client need to support some certain ciphersuite listed in SFR
              val ciphers = record.selectChildren("tls.handshake.ciphersuite")
              if(ciphers !== null){
                println("\t\ttest 1:ciphers>")
                val matches:MutableList<String> = mutableListOf()
                for(c in ciphers) {
                  println("\t\t\t>"+_showname(c))
                  val cipherName = _showname(c)
                  REQUIRED_CIPHERS_IN_SFR.forEach { it->
                    if(cipherName.indexOf(it)>=0){
                      matches.add(it)
                    }
                  }
                }
                //should support one of the ciphersuite listed in SFR:

                if(matches.size>=1){
                  println("supported ciphers in SFR requirement:"+matches.toString())
                } else {
                  //should assert
                  // println("found no ciphers which is required to implement.")
                  errs.checkThat(
                    a.Msg("Found no ciphers which is required to implement.)"),
                    true, IsEqual(false)
                  )
                }
              } else {
                //should assert
                errs.checkThat(
                  a.Msg("found no ciphers block in this tls packet"),
                  true, IsEqual(false)
                )
              }
              //test 2
              val tlsversions = record.selectChildren("tls.handshake.extensions.supported_version")
              if(tlsversions !== null){
                //implement TLS v1.2 [7], TLS v1.3 [11] or higher version of TLS;
                println("\t\ttest 2:versions>")//0x.0304,0303
                //var matches:MutableList<String> = mutableListOf()
                var supported = false
                for(ver in tlsversions) {
                  val found = _value(ver).toInt(16)
                  //println(found)
                  if(found == 0x0304 || found == 0x0303){
                    supported = true
                    break
                  }
                }
                if(supported){
                  println("The client supports tls v1.2 or later")
                } else {
                  errs.checkThat(
                    a.Msg("Failure : The client does not support tls v1.2 or later)"),
                    true, IsEqual(false)
                  )
                }
              } else {
                errs.checkThat(
                  a.Msg("Failure :  found no tlsversion block in this tls packet)"),
                  true, IsEqual(false)
                )
              }
              helloLookupDone = true
            }
            else if(hsType == 11 && !certLookupDone){ //Certificate
              //check : x509af.version should be larger than 0x02
              // val x50
              val n_ = record.selectChild("x509af.version")
              if(n_ !== null){
                val x509afver = _value(n_).toInt(16)
                println("test3 : x509 auth framework version is 0x$x509afver")
                if(x509afver>=2){
                  println("the value indicates version 3 or above ...  okay")
                } else {
                  //assert
                  errs.checkThat(
                    a.Msg("Failure : x509af version is insuffcient"),
                    true, IsEqual(false)
                  )
                }
              } else {

                errs.checkThat(
                  a.Msg("Failure : found no x509af version block in this tls packet"),
                  true, IsEqual(false))

              }
              ///////////////////////////////////////////////////
              //Check validity of certificate : expiration date
              //Packet Note:
              //  The value was put in the packet like below.
              //  It's the concatenated ascii codes which represents a date time value.
              //  3135303431323233353935395a => 1504009000000Z => 2015-04-09-00:00:00Z
              //  (The letters on hundreds/thousand place of the year are omitted.)
              //x509af.notBefore > x509af.utcTime
              //If the value is invalid we can catch an alert packet (content type=21)
              val nb_ = record.selectChild("x509af.notBefore")
              val na_ = record.selectChild("x509af.notAfter")
              if(nb_ !== null && na_ !== null){
                fun tls_utcdate(input:String):LocalDateTime{
                  val sb = StringBuffer()
                  input.chunked(2).forEach {
                    sb.append(Char( it.toInt(16)))
                  }
                  val dtf = DateTimeFormatter.ofPattern("yyMMddHHmmssX")
                  return LocalDateTime.parse(sb.toString(),dtf)
                }
                val nb = tls_utcdate(_value(nb_.selectChild("x509af.utcTime")))
                val na = tls_utcdate(_value(na_.selectChild("x509af.utcTime")))
                val now = LocalDateTime.now()
                println("test4: Cert Expiration check date should not before:$nb notafter:$na")
                if(now.isAfter(na) || now.isBefore(nb)){
                  certExpire = true//
                  certProblemFound = true//The value should be set to false until the end of the test
                  println("Failure : this cert is expired ")
                }
              } else {
                errs.checkThat(
                  a.Msg("Failure : found no expiration date records"),
                  true, IsEqual(false))
              }
              certLookupDone = true
            }
          }

          if(cnType == 21 && certExpire == true){ //Alert
            println("The cert is expired, but connection is gently canceled. okay")
            certProblemFound = false
          }

          i++
        }
      }
    }
    if(certProblemFound){
      errs.checkThat(
        a.Msg("Failure : there were problem in cert record, but connection was not canceled."),
        true, IsEqual(false))
    }
  }
  val OUT_PATH  = "../results/capture/"
  fun copyPcapToOutPath(pcap:Path,testlabel:String):Path
  {
    val outdir = File(Paths.get(OUT_PATH).toUri())
    if(!outdir.exists()){
     outdir.mkdir()
    } else if(!outdir.isDirectory){
      outdir.delete()
      outdir.mkdir()
    }
    val tstmp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
    val to = Paths.get(OUT_PATH,"${tstmp}-${testlabel}.pcap")
    try {
      Files.copy(pcap, to)
    } catch (e: IOException) {
      e.printStackTrace()
    }
    return to
  }

  fun isLockScreenEnbled():Boolean{
    var locked = false
    runBlocking {
      val response =
        client.execute(
          ShellCommandRequest(
            "dumpsys window | grep mDreamingLockscreen"
          ), adb.deviceSerial
        )
      if(response != null &&
        response.output.contains("mDreamingLockscreen=true"))
        locked=true
      println(response)
    }
    return locked
  }

  fun tlsCapturePacket(testlabel:String,testurl:String):Pair<String,Path> {

    var pcap:Path = Paths.get("/")
    var http_resp:String = ""

    runBlocking {
      //prerequite module check
      var cmdret = HostShellHelper.executeCommands("compgen -ac tshark")
      if(!cmdret.equals(0)){
        println("tshark is not found. please install the command to the environment")
        Assert.assertTrue(false)
        exitProcess(1)
      }
      val serial = adb.deviceSerial
      //Install prerequisite modules
      val pcap_apk=
        File(Paths.get("src", "test", "resources", "pcapdroid-debug.apk").toUri())
      var ret = AdamUtils.installApk(client, serial, pcap_apk, true)
      Assert.assertTrue(ret.startsWith("Success"))
      val browser_apk=
        File(Paths.get("src", "test", "resources", "openurl-debug.apk").toUri())
      ret = AdamUtils.installApk(client, serial, browser_apk, false)
      Assert.assertTrue(ret.startsWith("Success"))

      val response =
        client.execute(ShellCommandRequest(
          "am start -n com.emanuelef.remote_capture/.activities.CaptureCtrl"+
                  " -e action start"+
                  " -e pcap_dump_mode pcap_file"+
                  " -e pcap_name traffic.pcap"
          ),serial)

      println(response)

      //Launch packet capture software with uiautomator session
      //if it's first time we should say 'OK' to 3 dialogues,
      //after that we only need to say once.
      Thread.sleep(1000)
      UIAutomatorSession(adb,PKG_PCAPDROID).run {
        val label0= "${PKG_PCAPDROID}:id/allow_btn"
        println("pcapdroid ui check:"+exists(label0))
        if(exists(label0)){ tap(label0) } else return@run
        Thread.sleep(2000)
        UIAutomatorSession(adb,PKG_PCAPDROID).run level2@{
          val label1= "android:id/button1"
          //println(exists(label1))
          if(exists(label1)){ tap(label1) } else return@level2
          Thread.sleep(2000)
          UIAutomatorSession(adb,"com.android.vpndialogs").run level3@{
            val label2= "android:id/button1"
            //println(exists(label2))
            if(exists(label2)){ tap(label2) } else return@level3
          }
        }
      }
      Thread.sleep(3000)
      //Launch openurl app to access a certain website!
      client.execute(ShellCommandRequest(
        "am start -a android.intent.action.VIEW -n com.example.openurl/.MainActivity"+
                " -e openurl $testurl"
      ),serial)

      //Wait worker response on logcat and get return code from that
      val res:AdamUtils.LogcatResult? =
        AdamUtils.waitLogcatLine(100,"worker@return",adb)

      if(res !== null){
        println("worker@return=>"+res.text)
        //evaluate the return value
      } else {
        //res == null break *panic*
        println("we can't grab the return value from worker.")
        Assert.assertTrue(false)
      }
      //return value
      http_resp = res!!.text
      //
      Thread.sleep(500)
      //Open a connection(?) on the URL(??) and cast the response(???)
      //kill processes
      client.execute(ShellCommandRequest("am force-stop com.emanuelf.remote_capture"),serial)
      client.execute(ShellCommandRequest("am force-stop com.example.openurl"),serial)
      Thread.sleep(500)
      //pull a pdml file
      val src = "/storage/emulated/0/Download/PCAPdroid/traffic.pcap"
      val pcap0: Path = kotlin.io.path.createTempFile("t", ".pcap")
      AdamUtils.pullFile(src, pcap0.toString(), adb, true)
      //
      pcap = copyPcapToOutPath(pcap0,testlabel)
      Thread.sleep(3000)

      //convert pcap to pdml file to analyze
      val cmd="""\
tshark -r ${pcap.toAbsolutePath()} -o tls.debug_file:ssldebug.log \
-o tls.desegment_ssl_records:TRUE \
-o tls.desegment_ssl_application_data:TRUE -V -T pdml > ${pcap.toAbsolutePath()}.xml
"""
      cmdret = HostShellHelper.executeCommands(cmd)

      println(cmdret)
      Thread.sleep(1000)
      //return Pair<String,Path>(res!!.text,pcap)
    }
    return Pair(http_resp,pcap)
  }
}