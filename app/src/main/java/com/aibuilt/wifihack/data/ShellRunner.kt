package com.aibuilt.wifihack.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

object ShellRunner {

    /**
     * Opens a root shell, feeds it [script] lines, and emits every output line.
     * The flow completes when the shell exits; cancelling it kills the process.
     */
    fun stream(script: List<String>): Flow<String> = callbackFlow {
        val proc = try {
            ProcessBuilder("su").redirectErrorStream(true).start()
        } catch (e: Exception) {
            trySend("[!] Could not open root shell: ${e.message}")
            trySend("[!] A rooted device with 'su' is required.")
            close()
            return@callbackFlow
        }

        val readerJob = launch(Dispatchers.IO) {
            try {
                proc.inputStream.bufferedReader().forEachLine { trySend(it) }
            } catch (_: Exception) {
            }
        }

        launch(Dispatchers.IO) {
            try {
                proc.outputStream.bufferedWriter().use { w ->
                    for (line in script) {
                        w.write(line); w.newLine()
                    }
                    w.write("exit"); w.newLine(); w.flush()
                }
            } catch (_: Exception) {
            }
        }

        launch(Dispatchers.IO) {
            try {
                proc.waitFor()
            } catch (_: Exception) {
            }
            readerJob.join()
            trySend("[*] process finished")
            close()
        }

        awaitClose { runCatching { proc.destroy() } }
    }.flowOn(Dispatchers.IO)
}
