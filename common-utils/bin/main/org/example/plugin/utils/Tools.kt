package org.example.plugin.utils

import org.example.project.JUnitBridge
import java.io.File

fun FileResource(fname: String): File =
    File(JUnitBridge.resourceDir, fname)
