package com.awcjack.dualquickime.voice

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Serializes model startup and cleanup without blocking the keyboard thread. */
internal class VoiceSessionWorker {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "VoiceSessionWorker")
    }
    private val generation = AtomicInteger()

    // Called by the UI thread. A cancelled native load cannot be interrupted,
    // but its completion must never reopen the microphone or update the overlay.
    fun start(task: (isCurrent: () -> Boolean) -> Unit) {
        val session = generation.incrementAndGet()
        executor.execute {
            val isCurrent = { generation.get() == session }
            if (isCurrent()) task(isCurrent)
        }
    }

    fun cancel(cleanup: () -> Unit) {
        generation.incrementAndGet()
        executor.execute(cleanup)
    }

    fun close(cleanup: () -> Unit) {
        cancel(cleanup)
        executor.shutdown()
    }
}
