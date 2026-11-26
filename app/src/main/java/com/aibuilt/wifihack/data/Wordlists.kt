package com.aibuilt.wifihack.data

import android.content.Context
import com.aibuilt.wifihack.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class Credential(val user: String, val pass: String)

object Wordlists {

    // vendor, user, pass  (vendor "*" = global)
    @Volatile
    private var rows: List<Triple<String, String, String>>? = null
    private val mutex = Mutex()

    private suspend fun ensure(context: Context) {
        if (rows != null) return
        mutex.withLock {
            if (rows != null) return
            rows = withContext(Dispatchers.IO) {
                val out = ArrayList<Triple<String, String, String>>()
                runCatching {
                    context.resources.openRawResource(R.raw.router_wordlist)
                        .bufferedReader().useLines { lines ->
                            for (raw in lines) {
                                val line = raw.trim()
                                if (line.isEmpty() || line.startsWith("#")) continue
                                val parts = line.split(",")
                                if (parts.size < 3) continue
                                out.add(Triple(parts[0].trim(), parts[1].trim(), parts[2].trim()))
                            }
                        }
                }
                out
            }
        }
    }

    suspend fun forVendor(context: Context, vendor: String): List<Credential> {
        ensure(context)
        val all = rows ?: emptyList()
        val v = vendor.lowercase()
        val scoped = all.filter { it.first != "*" && v.contains(it.first.lowercase()) }
        val global = all.filter { it.first == "*" }
        return (scoped + global).map { Credential(it.second, it.third) }.distinct()
    }
}
