package com.aibuilt.wifihack.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

enum class RootStatus { UNKNOWN, CHECKING, ROOTED, NO_ROOT }

object RootProber {

    /** Runs `su -c id`. Returns true only if a root shell really answered as uid 0. */
    suspend fun isRooted(): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(4000) {
            try {
                val proc = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
                val out = BufferedReader(InputStreamReader(proc.inputStream)).readText()
                proc.waitFor()
                out.contains("uid=0")
            } catch (_: Exception) {
                false
            }
        } ?: false
    }

    /** True if any common su binary exists on disk (fast hint, not proof). */
    fun suBinaryPresent(): Boolean {
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/su/bin/su", "/vendor/bin/su", "/system/sbin/su"
        )
        return suPaths.any { runCatching { File(it).exists() }.getOrDefault(false) }
    }

    /** Reports which of the named tools are reachable (honest, nothing is executed). */
    suspend fun probeTools(names: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val dirs = listOf(
            "/system/bin", "/system/xbin", "/vendor/bin",
            "/sbin", "/su/bin", "/data/local/bin", "/data/local/tmp"
        )
        names.associateWith { name ->
            dirs.any { d -> runCatching { File(d, name).exists() }.getOrDefault(false) }
        }
    }
}
