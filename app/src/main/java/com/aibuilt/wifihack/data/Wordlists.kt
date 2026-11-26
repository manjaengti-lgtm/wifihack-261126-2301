package com.aibuilt.wifihack.data

import android.content.Context
import com.aibuilt.wifihack.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Credential(val vendor: String, val user: String, val pass: String)

object Wordlists {
    @Volatile private var cache: List<Credential>? = null

    private suspend fun all(context: Context): List<Credential> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val list = runCatching {
                context.resources.openRawResource(R.raw.router_wordlist)
                    .bufferedReader().readLines().mapNotNull { raw ->
                        val t = raw.trimEnd()
                        if (t.isBlank() || t.trimStart().startsWith("#")) return@mapNotNull null
                        val cols = t.split('\t')
                        if (cols.size < 3) return@mapNotNull null
                        Credential(cols[0].trim(), cols[1], cols[2])
                    }
            }.getOrDefault(emptyList())
            cache = list
            list
        }
    }

    /** Vendor-scoped guesses first, then every global (*) guess, de-duplicated. */
    suspend fun forVendor(context: Context, vendor: String): List<Credential> {
        val everything = all(context)
        val vendorKey = vendor.substringBefore('/').trim().lowercase()
        val scoped = everything.filter {
            it.vendor != "*" && vendorKey.isNotEmpty() &&
                (it.vendor.lowercase().contains(vendorKey) || vendorKey.contains(it.vendor.lowercase()))
        }
        val global = everything.filter { it.vendor == "*" }
        return (scoped + global).distinctBy { "${it.user}\u0000${it.pass}" }
    }
}
