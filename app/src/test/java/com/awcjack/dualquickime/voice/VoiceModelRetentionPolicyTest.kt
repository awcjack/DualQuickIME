package com.awcjack.dualquickime.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceModelRetentionPolicyTest {
    private val gib = 1024L * 1024 * 1024

    @Test fun defaultKeepsTheModelBeyondThirtySecondsAndReleasesAtTwoMinutes() {
        assertNull(reason(idleMillis = 30_000))
        assertNull(reason(idleMillis = 119_999))
        assertEquals("idle timeout", reason(idleMillis = 120_000))
    }

    @Test fun configuredTimeoutChangesWhenIdleWeightsAreReleased() {
        assertEquals("idle timeout", reason(idleMillis = 30_000, seconds = 30))
        assertNull(reason(idleMillis = 120_000, seconds = 300))
        assertEquals("idle timeout", reason(idleMillis = 300_000, seconds = 300))
    }

    @Test fun memoryPressureAndScreenLockReleaseEarlyButNeverWhileBusy() {
        assertEquals("screen locked", reason(locked = true))
        assertEquals("low available memory", reason(low = true))
        assertEquals("low available memory", reason(available = gib))
        assertEquals("low available memory", reason(available = gib + 1, total = 16 * gib))
        assertEquals("low available memory", reason(available = 2 * gib, threshold = gib))
        assertNull(reason(available = gib + 1))
        assertNull(reason(available = 0)) // Unavailable system reading.
        assertNull(reason(idleMillis = 600_000, busy = true, locked = true, low = true))
    }

    private fun reason(
        idleMillis: Long = 0,
        seconds: Int = VoiceModelRetentionPolicy.DEFAULT_TIMEOUT_SECONDS,
        busy: Boolean = false,
        locked: Boolean = false,
        low: Boolean = false,
        available: Long = 4 * gib,
        total: Long = 8 * gib,
        threshold: Long = gib / 4
    ) = VoiceModelRetentionPolicy.releaseReason(
        idleMillis, seconds, busy, locked, low, available, total, threshold
    )
}
