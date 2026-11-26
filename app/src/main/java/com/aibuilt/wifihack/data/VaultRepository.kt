package com.aibuilt.wifihack.data

import android.content.Context
import com.aibuilt.wifihack.model.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class VaultRepository(context: Context) {

    private val file = File(context.filesDir, "vault.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val mutex = Mutex()

    private val _entries = MutableStateFlow<List<VaultEntry>>(emptyList())
    val entries: StateFlow<List<VaultEntry>> = _entries.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = runCatching {
                if (file.exists())
                    json.decodeFromString(ListSerializer(VaultEntry.serializer()), file.readText())
                else emptyList()
            }.getOrDefault(emptyList())
            _entries.value = list.sortedByDescending { it.timestamp }
        }
    }

    private suspend fun persist(list: List<VaultEntry>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sorted = list.sortedByDescending { it.timestamp }
            runCatching {
                file.writeText(json.encodeToString(ListSerializer(VaultEntry.serializer()), sorted))
            }
            _entries.value = sorted
        }
    }

    suspend fun add(entry: VaultEntry) = persist(_entries.value + entry)

    suspend fun update(entry: VaultEntry) =
        persist(_entries.value.map { if (it.id == entry.id) entry else it })

    suspend fun delete(id: String) = persist(_entries.value.filterNot { it.id == id })
}
