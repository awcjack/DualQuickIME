package com.awcjack.dualquickime.data

import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class PronunciationRepositoryTest {
    private class QueueExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun runNext() { tasks.removeFirst().run() }
    }

    private fun table() = PronunciationTable.parse("6211\tngo5\n".byteInputStream())

    @Test fun coldLookupsNeverReadOrScheduleDictionaryWork() {
        val worker = QueueExecutor()
        var loads = 0
        val repository = PronunciationRepository(worker) { _, _ -> loads++; table() }
        repeat(100) { assertNull(repository.readingFor("我", PronunciationMode.CANTONESE)) }
        assertEquals(0, loads)
        assertTrue(worker.tasks.isEmpty())
        val ready = repository.preload(PronunciationMode.CANTONESE)
        assertFalse(ready.isDone)
        assertEquals(0, loads)
        assertNull(repository.readingFor("我", PronunciationMode.CANTONESE))
        worker.runNext()
        assertTrue(ready.isDone)
        assertEquals("ngo5", repository.readingFor("我", PronunciationMode.CANTONESE))
    }

    @Test fun repeatedPreloadsShareOneLoadAndWarmLookupsStayCached() {
        val worker = QueueExecutor()
        var loads = 0
        val repository = PronunciationRepository(worker) { _, _ -> loads++; table() }
        val first = repository.preload(PronunciationMode.CANTONESE)
        assertSame(first, repository.preload(PronunciationMode.CANTONESE))
        assertEquals(1, worker.tasks.size)
        worker.runNext()
        repeat(100) {
            assertEquals("ngo5", repository.readingFor("我", PronunciationMode.CANTONESE))
            assertTrue(repository.preload(PronunciationMode.CANTONESE).isDone)
        }
        assertEquals(1, loads)
        assertTrue(worker.tasks.isEmpty())
    }

    @Test fun labelsCanRefreshOnTheCallbackExecutorAfterLoading() {
        val worker = QueueExecutor()
        val ui = QueueExecutor()
        val repository = PronunciationRepository(worker) { _, _ -> table() }
        var refreshed = false
        repository.preload(PronunciationMode.CANTONESE).thenRunAsync({
            assertEquals("ngo5", repository.readingFor("我", PronunciationMode.CANTONESE))
            refreshed = true
        }, ui)
        worker.runNext()
        assertFalse(refreshed)
        ui.runNext()
        assertTrue(refreshed)
    }

    @Test fun destroyingACallerCancelsItsRefreshWithoutCancelingSharedLoading() {
        val worker = QueueExecutor()
        val ui = QueueExecutor()
        val repository = PronunciationRepository(worker) { _, _ -> table() }
        val loading = repository.preload(PronunciationMode.CANTONESE)
        var refreshed = false
        val callback = loading.thenRunAsync({ refreshed = true }, ui)
        worker.runNext()
        callback.cancel(false)
        ui.runNext()
        assertFalse(refreshed)
        assertFalse(loading.isCancelled)
        assertEquals("ngo5", repository.readingFor("我", PronunciationMode.CANTONESE))
    }

    @Test fun failedLoadsCanRetryAndDoNotBlockTheOtherLanguage() {
        val worker = QueueExecutor()
        var fail = true
        val repository = PronunciationRepository(worker) { mode, _ ->
            if (mode == PronunciationMode.CANTONESE && fail) error("Cannot read dictionary")
            table()
        }
        val failed = repository.preload(PronunciationMode.CANTONESE)
        val other = repository.preload(PronunciationMode.MANDARIN)
        worker.runNext()
        assertTrue(failed.isCompletedExceptionally)
        assertNull(repository.readingFor("我", PronunciationMode.CANTONESE))
        worker.runNext()
        assertTrue(other.isDone)
        fail = false
        repository.preload(PronunciationMode.CANTONESE)
        worker.runNext()
        assertEquals("ngo5", repository.readingFor("我", PronunciationMode.CANTONESE))
    }

    @Test fun disabledModeDoesNotCreateAnyWork() {
        val worker = QueueExecutor()
        val repository = PronunciationRepository(worker) { _, _ -> error("Must not load") }
        assertNull(repository.preload(PronunciationMode.NONE).getNow(null))
        assertNull(repository.readingFor("我", PronunciationMode.NONE))
        assertTrue(worker.tasks.isEmpty())
    }
    @Test fun memoryPressureEvictsReadingsAndBlocksLoadingUntilResumed() {
        val worker = QueueExecutor()
        var loads = 0
        val repository = PronunciationRepository(worker) { _, _ -> loads++; table() }
        repository.preload(PronunciationMode.CANTONESE)
        worker.runNext()
        repository.releaseMemory()
        assertNull(repository.readingFor("我", PronunciationMode.CANTONESE))
        assertNull(repository.preload(PronunciationMode.CANTONESE).join())
        assertTrue(worker.tasks.isEmpty())
        repository.resume()
        repository.preload(PronunciationMode.CANTONESE)
        worker.runNext()
        assertEquals(2, loads)
        assertEquals("ngo5", repository.readingFor("我", PronunciationMode.CANTONESE))
    }

    @Test fun canceledQueuedLoadCannotReplaceANewLoad() {
        val worker = QueueExecutor()
        var loads = 0
        val repository = PronunciationRepository(worker) { _, _ -> loads++; table() }
        val old = repository.preload(PronunciationMode.CANTONESE)
        repository.releaseMemory()
        assertTrue(old.isCancelled)
        repository.resume()
        val replacement = repository.preload(PronunciationMode.CANTONESE)
        worker.runNext()
        assertEquals(0, loads)
        assertSame(replacement, repository.preload(PronunciationMode.CANTONESE))
        worker.runNext()
        assertEquals(1, loads)
        assertEquals("ngo5", repository.readingFor("我", PronunciationMode.CANTONESE))
    }

    @Test fun inFlightLoadCannotRepublishAfterEviction() {
        val worker = QueueExecutor()
        lateinit var repository: PronunciationRepository
        repository = PronunciationRepository(worker) { _, active ->
            assertTrue(active())
            repository.releaseMemory()
            assertFalse(active())
            table()
        }
        val pending = repository.preload(PronunciationMode.CANTONESE)
        worker.runNext()
        assertTrue(pending.isCancelled)
        assertNull(repository.readingFor("我", PronunciationMode.CANTONESE))
    }

    @Test fun allocationFailureDoesNotEscapeWorkerAndEvictsOtherLanguages() {
        val worker = QueueExecutor()
        var fail = false
        val repository = PronunciationRepository(worker) { _, _ ->
            if (fail) throw OutOfMemoryError("Simulated allocation failure")
            table()
        }
        repository.preload(PronunciationMode.CANTONESE)
        worker.runNext()
        fail = true
        val failed = repository.preload(PronunciationMode.MANDARIN)
        worker.runNext() // Must contain the error instead of killing the IME worker.
        assertTrue(failed.isCompletedExceptionally)
        assertFalse(failed.isCancelled)
        assertTrue(runCatching { failed.join() }.exceptionOrNull()?.cause is OutOfMemoryError)
        assertNull(repository.readingFor("我", PronunciationMode.CANTONESE))
        repository.preload(PronunciationMode.CANTONESE)
        assertTrue(worker.tasks.isEmpty())
        fail = false
        repository.resume()
        repository.preload(PronunciationMode.MANDARIN)
        worker.runNext()
        assertEquals("ngo5", repository.readingFor("我", PronunciationMode.MANDARIN))
    }

}
