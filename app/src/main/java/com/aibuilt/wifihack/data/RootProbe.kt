package com.aibuilt.wifihack.data

import com.aibuilt.wifihack.root.RootProber

/**
 * Deprecated shim. The real, single implementation now lives in
 * [com.aibuilt.wifihack.root.RootProber]. Kept only so older callers compile.
 */
@Deprecated(
    "Use com.aibuilt.wifihack.root.RootProber instead.",
    ReplaceWith("RootProber", "com.aibuilt.wifihack.root.RootProber")
)
object RootProbe {
    suspend fun isRooted(): Boolean = RootProber.isRooted()
}
