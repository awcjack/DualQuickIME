package com.awcjack.dualquickime.data

import java.util.concurrent.Executors

/** Optional dictionaries load serially to avoid overlapping allocation peaks. */
object KeyboardDataWorker {
    val executor = Executors.newSingleThreadExecutor { task ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "KeyboardDataLoader").apply { isDaemon = true }
    }
}
