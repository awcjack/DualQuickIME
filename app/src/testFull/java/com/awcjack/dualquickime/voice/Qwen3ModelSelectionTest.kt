package com.awcjack.dualquickime.voice

import android.content.Context
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.awcjack.dualquickime.R
import com.awcjack.dualquickime.SettingsActivity
import com.awcjack.dualquickime.theme.ThemeManager
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowProcess

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Qwen3ModelSelectionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val largeModel = VoiceModelType.QWEN3_ASR_1_7B

    @Before fun setUp() {
        context.getSharedPreferences("dualquick_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        ThemeManager.invalidateCache()
        cleanModelFiles()
    }

    @After fun tearDown() = cleanModelFiles()

    @Test fun existingQwenSelectionIsPreservedAndLargerModelCanBeSelected() {
        ThemeManager.setVoiceModelType(context, "qwen3_asr")
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertEquals("Qwen3-ASR 0.6B", activity.findViewById<TextView>(R.id.textSelectedModel).text.toString())
            activity.findViewById<Button>(R.id.btnSelectModel).performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val list = dialog.listView
            val index = (0 until list.count).first {
                list.adapter.getItem(it).toString().startsWith("Qwen3-ASR 1.7B")
            }
            list.performItemClick(null, index, list.adapter.getItemId(index))
            assertEquals(largeModel.id, ThemeManager.getVoiceModelType(context))
            assertEquals("Qwen3-ASR 1.7B", activity.findViewById<TextView>(R.id.textSelectedModel).text.toString())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun downloadedSmallModelDoesNotMakeLargeModelAvailable() {
        createModelFiles(VoiceModelType.QWEN3_ASR, mapOf(
            "conv_frontend.onnx" to 44_148_281L,
            "encoder.int8.onnx" to 182_491_662L,
            "decoder.int8.onnx" to 755_914_231L,
            "tokenizer/vocab.json" to 2_776_833L,
            "tokenizer/tokenizer_config.json" to 12_487L,
            "tokenizer/merges.txt" to 1_671_853L
        ))
        assertTrue(ModelDownloadManager.isModelDownloaded(context, VoiceModelType.QWEN3_ASR))
        assertFalse(ModelDownloadManager.isModelDownloaded(context, largeModel))
    }

    @Test fun largeModelProgressUsesLongAndRequiresAllTokenizerFiles() {
        createModelFiles(largeModel, mapOf(
            "conv_frontend.onnx" to 48_080_441L,
            "encoder.int8.onnx" to 314_222_162L,
            "decoder.int8.onnx" to 2_037_458_645L,
            "tokenizer/vocab.json" to 2_776_833L,
            "tokenizer/tokenizer_config.json" to 12_487L,
            "tokenizer/merges.txt" to 1_671_853L
        ))
        val (downloaded, total) = ModelDownloadManager.getDownloadProgress(context, largeModel)
        assertTrue(total > Int.MAX_VALUE)
        assertEquals(largeModel.sizeBytes + 630_000L, total)
        assertEquals(total, downloaded)
        assertTrue(ModelDownloadManager.isModelDownloaded(context, largeModel))
        File(context.filesDir, "${largeModel.modelDir}/tokenizer/merges.txt").delete()
        assertFalse(ModelDownloadManager.isModelDownloaded(context, largeModel))
        assertEquals(total - 1_671_853L, ModelDownloadManager.getDownloadProgress(context, largeModel).first)
    }

    @Test
    @Config(shadows = [Process32BitShadow::class])
    fun rejectsLargeModelOn32BitAndroidBeforeCreatingDownloadFiles() {
        val finished = CountDownLatch(1)
        val error = AtomicReference<String>()
        ModelDownloadManager.downloadModel(context, largeModel, object : ModelDownloadManager.DownloadCallback {
            override fun onProgress(bytesDownloaded: Long, totalBytes: Long, currentFile: String) = Unit
            override fun onComplete() = finished.countDown()
            override fun onError(message: String) {
                error.set(message)
                finished.countDown()
            }
        })
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertEquals(context.getString(R.string.voice_model_requires_64_bit), error.get())
        assertFalse(File(context.filesDir, largeModel.modelDir).exists())
    }

    @Implements(android.os.Process::class)
    class Process32BitShadow : ShadowProcess() {
        companion object {
            @JvmStatic @Implementation fun is64Bit(): Boolean = false
        }
    }

    private fun createModelFiles(model: VoiceModelType, sizes: Map<String, Long>) {
        sizes.forEach { (name, size) ->
            sparseFile(File(context.filesDir, "${model.modelDir}/$name"), size)
        }
        sparseFile(File(context.filesDir, ModelDownloadManager.VAD_MODEL_FILE), 630_000L)
    }

    // Sparse files exercise >2 GB progress without allocating model weights.
    private fun sparseFile(file: File, size: Long) {
        file.parentFile!!.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(size) }
    }

    private fun cleanModelFiles() {
        listOf(VoiceModelType.QWEN3_ASR, largeModel).forEach {
            File(context.filesDir, it.modelDir).deleteRecursively()
        }
        File(context.filesDir, ModelDownloadManager.VAD_MODEL_FILE).delete()
    }
}
