package com.awcjack.dualquickime.voice

/** Retention applies only to the large Qwen recognizers. */
internal object VoiceModelRetentionPolicy {
    const val DEFAULT_TIMEOUT_SECONDS = 120
    val timeoutOptionsSeconds = listOf(30, 60, 120, 300, 600)
    private const val GIB = 1024L * 1024 * 1024

    fun releaseReason(
        idleMillis: Long,
        timeoutSeconds: Int,
        busy: Boolean,
        screenLocked: Boolean,
        lowMemory: Boolean,
        availableBytes: Long,
        totalBytes: Long,
        systemThresholdBytes: Long
    ): String? {
        if (busy) return null
        if (screenLocked) return "screen locked"
        // Leave headroom for other apps before reaching Android's low-memory
        // threshold. This is a conservative budget, not a measured model size.
        val reserve = maxOf(GIB, totalBytes / 8, systemThresholdBytes * 2)
        if (lowMemory || availableBytes in 1..reserve) return "low available memory"
        if (idleMillis >= timeoutSeconds * 1000L) return "idle timeout"
        return null
    }
}
