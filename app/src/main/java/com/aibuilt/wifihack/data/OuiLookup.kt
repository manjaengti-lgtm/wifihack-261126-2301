package com.aibuilt.wifihack.data

import android.content.Context
import com.aibuilt.wifihack.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object OuiLookup {

    @Volatile
    private var table: Map<String, String>? = null
    private val mutex = Mutex()
    private val lock = Any()

    private fun loadTable(context: Context): Map<String, String> {
        val map = HashMap<String, String>()
        runCatching {
            context.resources.openRawResource(R.raw.oui_prefixes)
                .bufferedReader().useLines { lines ->
                    for (raw in lines) {
                        val line = raw.trim()
                        if (line.isEmpty() || line.startsWith("#")) continue
                        val idx = line.indexOfFirst { it == ' ' || it == '\t' }
                        if (idx <= 0) continue
                        val prefix = normalize(line.substring(0, idx))
                        val vendor = line.substring(idx + 1).trim()
                        if (prefix.length == 6 && vendor.isNotEmpty()) map[prefix] = vendor
                    }
                }
        }
        return map
    }

    suspend fun ensureLoaded(context: Context) {
        if (table != null) return
        mutex.withLock {
            if (table != null) return
            table = withContext(Dispatchers.IO) { loadTable(context) }
        }
    }

    fun ensureLoadedBlocking(context: Context) {
        if (table != null) return
        synchronized(lock) {
            if (table != null) return
            table = loadTable(context)
        }
    }

    private fun normalize(value: String): String =
        value.uppercase().filter { it.isLetterOrDigit() }

    suspend fun vendorFor(context: Context, bssid: String): String {
        ensureLoaded(context)
        return lookup(bssid)
    }

    fun vendorForSync(context: Context, bssid: String): String {
        ensureLoadedBlocking(context)
        return lookup(bssid)
    }

    private fun lookup(bssid: String): String {
        val key = normalize(bssid).take(6)
        if (key.length < 6) return "Unknown"
        return table?.get(key) ?: "Unknown"
    }
}
