package com.awcjack.dualquickime.data

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/** Loads immutable dictionaries once, without doing I/O in candidate lookups. */
class PronunciationRepository(
    private val executor: Executor,
    private val loader: (PronunciationMode) -> PronunciationTable
) {
    private val ready = ConcurrentHashMap<PronunciationMode, PronunciationTable>()
    private val loading = mutableMapOf<PronunciationMode, CompletableFuture<PronunciationTable?>>()

    fun readingFor(candidate: String, mode: PronunciationMode, precedingCharacter: String = ""): String? =
        ready[mode]?.readingFor(candidate, precedingCharacter)

    fun preload(mode: PronunciationMode): CompletableFuture<PronunciationTable?> {
        if (mode == PronunciationMode.NONE) return CompletableFuture.completedFuture(null)
        val future = synchronized(loading) {
            ready[mode]?.let { return CompletableFuture.completedFuture(it) }
            loading[mode]?.let { return it }
            CompletableFuture<PronunciationTable?>().also { loading[mode] = it }
        }
        // Never hold the cache lock while scheduling or reading assets.
        try {
            executor.execute {
                try {
                    val table = loader(mode)
                    ready[mode] = table
                    synchronized(loading) { loading.remove(mode) }
                    future.complete(table)
                } catch (e: Exception) {
                    failed(mode, future, e)
                }
            }
        } catch (e: Exception) {
            failed(mode, future, e)
        }
        return future
    }

    private fun failed(mode: PronunciationMode, future: CompletableFuture<PronunciationTable?>, error: Exception) {
        synchronized(loading) { if (loading[mode] === future) loading.remove(mode) }
        future.completeExceptionally(error)
    }
}
