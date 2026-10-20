package com.awcjack.dualquickime.data

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/** Loads immutable dictionaries once, without doing I/O in candidate lookups. */
class PronunciationRepository(
    private val executor: Executor,
    private val loader: (PronunciationMode, () -> Boolean) -> PronunciationTable
) {
    private val ready = ConcurrentHashMap<PronunciationMode, PronunciationTable>()
    private val loading = mutableMapOf<PronunciationMode, CompletableFuture<PronunciationTable?>>()
    @Volatile private var generation = 0
    @Volatile private var suspended = false

    fun releaseMemory() {
        evict(null, null)
    }

    private fun evict(failedFuture: CompletableFuture<PronunciationTable?>?, error: OutOfMemoryError?) {
        val pending = synchronized(loading) {
            suspended = true
            generation++
            ready.clear()
            loading.values.toList().also { loading.clear() }
        }
        pending.forEach {
            if (it === failedFuture) it.completeExceptionally(error!!) else it.cancel(false)
        }
    }

    fun resume() { suspended = false }

    fun readingFor(candidate: String, mode: PronunciationMode, precedingCharacter: String = ""): String? =
        ready[mode]?.readingFor(candidate, precedingCharacter)

    fun preload(mode: PronunciationMode): CompletableFuture<PronunciationTable?> {
        if (mode == PronunciationMode.NONE) return CompletableFuture.completedFuture(null)
        val token: Int
        val future = synchronized(loading) {
            if (suspended) return CompletableFuture.completedFuture(null)
            ready[mode]?.let { return CompletableFuture.completedFuture(it) }
            loading[mode]?.let { return it }
            token = generation
            CompletableFuture<PronunciationTable?>().also { loading[mode] = it }
        }
        // Never hold the cache lock while scheduling or reading assets.
        try {
            executor.execute {
                try {
                    val active = { !suspended && generation == token && !future.isCancelled }
                    if (!active()) return@execute
                    val table = loader(mode, active)
                    val published = synchronized(loading) {
                        if (active()) {
                            ready[mode] = table
                            if (loading[mode] === future) loading.remove(mode)
                            true
                        } else false
                    }
                    if (published) future.complete(table)
                } catch (e: OutOfMemoryError) {
                    // Optional decorations must not crash the whole IME.
                    evict(future, e)
                } catch (e: Exception) {
                    failed(mode, future, e)
                }
            }
        } catch (e: OutOfMemoryError) {
            evict(future, e)
        } catch (e: Exception) {
            failed(mode, future, e)
        }
        return future
    }

    private fun failed(mode: PronunciationMode, future: CompletableFuture<PronunciationTable?>, error: Throwable) {
        synchronized(loading) { if (loading[mode] === future) loading.remove(mode) }
        future.completeExceptionally(error)
    }
}
