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
        val repository = PronunciationRepository(worker) { loads++; table() }
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
        val repository = PronunciationRepository(worker) { loads++; table() }
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
        val repository = PronunciationRepository(worker) { table() }
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
        val repository = PronunciationRepository(worker) { table() }
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
        val repository = PronunciationRepository(worker) { mode ->
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
        val repository = PronunciationRepository(worker) { error("Must not load") }
        assertNull(repository.preload(PronunciationMode.NONE).getNow(null))
        assertNull(repository.readingFor("我", PronunciationMode.NONE))
        assertTrue(worker.tasks.isEmpty())
    }
}
