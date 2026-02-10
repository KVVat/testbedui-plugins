package com.example.test_suites.utils

import java.text.DecimalFormat
import org.junit.rules.TestName
import org.junit.runner.Description

class TestAssertLogger(name: TestName){
  var inc:Int = 0;
  val name: TestName =name
  fun Msg(desc:String):String?{
    inc++;
    val line = name.methodName + "(" + DecimalFormat("000").format(inc) +"):"+ desc;
    println(line)
    return line
  }
}