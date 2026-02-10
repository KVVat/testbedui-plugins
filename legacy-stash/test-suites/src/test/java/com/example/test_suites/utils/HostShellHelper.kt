package com.example.test_suites.utils

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.Writer


class HostShellHelper {
  companion object {
    @Throws(IOException::class)
    fun executeCommands(script: String):Int {
      val tempScript = createTempScript(script)
      try {
        val pb = ProcessBuilder("bash", tempScript.toString())
        pb.inheritIO()
        val process = pb.start()
        process.waitFor()
        return process.exitValue()

      } finally {
        tempScript.delete()
      }
    }

    @Throws(IOException::class)
    fun createTempScript(script:String): File {
      val tempScript = File.createTempFile("script", null)
      val streamWriter: Writer = OutputStreamWriter(
        FileOutputStream(
          tempScript
        )
      )
      val printWriter: PrintWriter = PrintWriter(streamWriter)
      printWriter.println("#!/bin/bash")
      printWriter.println(script)
      printWriter.close()
      return tempScript
    }
  }
}