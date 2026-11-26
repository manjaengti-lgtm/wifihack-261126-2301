package com.aibuilt.wifihack.data

import android.content.Context
import com.aibuilt.wifihack.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object OuiLookup {
    private val map = HashMap<String, String>()
    @Volatile private var loaded = false
    private val mutex = Mutex()

    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) {
                runCatching {
                    context.resources.openRawResource(R.raw.oui_prefixes)
                        .bufferedReader().useLines { lines ->
                            for (line in lines) {
                                val t = line.trim()
                                if (t.isEmpty() || t.startsWith("#")) continue
                                val parts =
                                    if (t.contains('\t')) t.split('\t', limit = 2)
                                    else t.split(Regex("\\s+"), limit = 2)
                                if (parts.size == 2 && parts[1].isNotBlank()) {
                                    map[normalize(parts[0])] = parts[1].trim()
                                }
                            }
                        }
                }
                loaded = true
            }
        }
    }

    private fun normalize(prefix: String): String =
        prefix.uppercase().replace(":", "").replace("-", "").filter { it.isLetterOrDigit() }.take(6)

    fun vendorFor(bssid: String?): String {
        if (bssid.isNullOrBlank()) return "Unknown"
        val key = normalize(bssid)
        if (key.length < 6) return "Unknown"
        return map[key] ?: "Unknown"
    }
}
