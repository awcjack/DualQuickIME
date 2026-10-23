package com.awcjack.dualquickime.voice

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class VoiceSessionWorkerTest {
    @Test fun cancellingSlowLoadDiscardsCompletionAndCleansUpBeforeRetry() {
        val worker = VoiceSessionWorker()
        val loading = CountDownLatch(1)
        val finishLoading = CountDownLatch(1)
        val retried = CountDownLatch(1)
        val microphoneOpened = AtomicBoolean(false)
        val cleaned = AtomicBoolean(false)
        val retryReady = AtomicBoolean(false)
        try {
            worker.start { isCurrent ->
                loading.countDown()
                check(finishLoading.await(5, TimeUnit.SECONDS))
                if (isCurrent()) microphoneOpened.set(true)
            }
            assertTrue(loading.await(5, TimeUnit.SECONDS))
            worker.cancel { cleaned.set(true) }
            worker.start { isCurrent ->
                retryReady.set(cleaned.get() && isCurrent())
                retried.countDown()
            }
            finishLoading.countDown()
            assertTrue(retried.await(5, TimeUnit.SECONDS))
            assertFalse(microphoneOpened.get())
            assertTrue(retryReady.get())
        } finally {
            finishLoading.countDown()
            worker.close {}
        }
    }

    @Test fun teardownInvalidatesAlreadyPostedUiCompletionAndWaitsForLoading() {
        val worker = VoiceSessionWorker()
        val loading = CountDownLatch(1)
        val finishLoading = CountDownLatch(1)
        val released = CountDownLatch(1)
        var completionIsCurrent: (() -> Boolean)? = null
        worker.start { isCurrent ->
            completionIsCurrent = isCurrent
            loading.countDown()
            check(finishLoading.await(5, TimeUnit.SECONDS))
        }
        try {
            assertTrue(loading.await(5, TimeUnit.SECONDS))
            assertTrue(completionIsCurrent!!.invoke())
            worker.close { released.countDown() }
            assertFalse(completionIsCurrent!!.invoke())
            assertEquals(1L, released.count)
        } finally {
            finishLoading.countDown()
        }
        assertTrue(released.await(5, TimeUnit.SECONDS))
    }
}
