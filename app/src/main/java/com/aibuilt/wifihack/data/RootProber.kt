package com.aibuilt.wifihack.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootProber {

    private val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su",
        "/su/bin/su", "/vendor/bin/su", "/data/local/xbin/su", "/data/local/bin/su"
    )

    fun suBinaryPresent(): Boolean = suPaths.any { runCatching { File(it).exists() }.getOrDefault(false) }

    suspend fun isRooted(): Boolean = withContext(Dispatchers.IO) {
        try {
            val proc = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            out.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    suspend fun probeTools(tools: List<String>): List<Pair<String, Boolean>> =
        withContext(Dispatchers.IO) {
            tools.map { tool -> tool to toolPresent(tool) }
        }

    private fun toolPresent(tool: String): Boolean {
        val dirs = listOf(
            "/system/bin", "/system/xbin", "/vendor/bin",
            "/data/local/bin", "/data/data/com.termux/files/usr/bin", "/sbin"
        )
        if (dirs.any { runCatching { File(it, tool).exists() }.getOrDefault(false) }) return true
        return try {
            val proc = ProcessBuilder("sh", "-c", "command -v $tool").redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            out.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
