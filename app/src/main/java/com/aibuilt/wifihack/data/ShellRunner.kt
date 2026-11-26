package com.aibuilt.wifihack.data

object ShellRunner {

    /** Runs a command, delivering each output line to [onLine]. Returns the exit code, or -1 on error. */
    fun stream(useRoot: Boolean, command: String, onLine: (String) -> Unit): Int {
        return try {
            val pb = if (useRoot)
                ProcessBuilder("su", "-c", command)
            else
                ProcessBuilder("sh", "-c", command)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            proc.inputStream.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    onLine(line)
                    line = reader.readLine()
                }
            }
            proc.waitFor()
        } catch (e: Exception) {
            onLine("[err] ${e.message}")
            -1
        }
    }
}
