package com.example.test_suites.utils

import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LogcatUtils {
    //private val BUFFER_BEGIN_RE = Pattern.compile("--------- beginning of (.*)")

    private val sinceFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    /*class Log(rawText: String, val timeZone: TimeZone) :
        LogLine(LOG_LINE_RE.matcher(rawText).also { it.find() }) {
        val date = Calendar.getInstance(timeZone).apply {
            set(Calendar.MONTH, matcher.group(3)!!.toInt() - 1)
            set(Calendar.DAY_OF_MONTH, matcher.group(4)!!.toInt())
            set(Calendar.HOUR_OF_DAY, matcher.group(5)!!.toInt())
            set(Calendar.MINUTE, matcher.group(6)!!.toInt())
            set(Calendar.SECOND, matcher.group(7)!!.toInt())
            set(Calendar.MILLISECOND, matcher.group(8)!!.toInt())
        }

        val pid = matcher.group(9)
        val tid = matcher.group(10)
        val level = matcher.group(11)!![0]
        override val tag = matcher.group(12)
        override val text = matcher.group(13)
    }*/

}