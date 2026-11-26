package com.aibuilt.wifihack.data

/**
 * Deprecated shim. The real, single implementation now lives in
 * [com.aibuilt.wifihack.data.RootProber]. Kept only so older callers compile.
 */
@Deprecated(
    "Use com.aibuilt.wifihack.data.RootProber instead.",
    ReplaceWith("RootProber", "com.aibuilt.wifihack.data.RootProber")
)
object RootProbe {
    suspend fun isRooted(): Boolean = RootProber.isRooted()
}
