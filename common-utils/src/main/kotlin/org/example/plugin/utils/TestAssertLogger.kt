package org.example.plugin.utils

import java.text.DecimalFormat
import org.junit.rules.TestName
import org.junit.runner.Description

class TestAssertLogger(name: TestName){
  var inc:Int = 0;
  val name: TestName =name
    fun msg(desc:String):String?{
        inc++;
        val line = name.methodName + "(" + DecimalFormat("000").format(inc) +"):"+ desc;
        logi(line)
        return line
    }
}