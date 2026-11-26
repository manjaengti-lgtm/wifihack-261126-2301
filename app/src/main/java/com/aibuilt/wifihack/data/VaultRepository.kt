package com.aibuilt.wifihack.data

import android.content.Context
import com.aibuilt.wifihack.model.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class VaultRepository(context: Context) {

    private val file = File(context.applicationContext.filesDir, "vault.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutex = Mutex()

    private val _entries = MutableStateFlow<List<VaultEntry>>(emptyList())
    val entries: StateFlow<List<VaultEntry>> = _entries

    suspend fun load() = withContext(Dispatchers.IO) {
        if (file.exists()) {
            runCatching {
                _entries.value = json.decodeFromString(file.readText())
            }
        }
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        runCatching { file.writeText(json.encodeToString(_entries.value)) }
        Unit
    }

    suspend fun add(entry: VaultEntry) = mutex.withLock {
        _entries.value = _entries.value.filterNot {
            it.bssid == entry.bssid && it.ssid == entry.ssid
        } + entry
        persist()
    }

    suspend fun update(entry: VaultEntry) = mutex.withLock {
        _entries.value = _entries.value.map { if (it.id == entry.id) entry else it }
        persist()
    }

    suspend fun delete(id: String) = mutex.withLock {
        _entries.value = _entries.value.filterNot { it.id == id }
        persist()
    }
}
