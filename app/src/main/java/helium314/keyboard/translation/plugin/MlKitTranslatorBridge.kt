// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.translation.plugin

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MlKitTranslatorBridge(private val context: Context) {
    private val modelReady = ConcurrentHashMap<String, Boolean>()
    private val downloading = ConcurrentHashMap<String, AtomicBoolean>()

    private val translators = object : LruCache<String, Translator>(2) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String?,
            oldValue: Translator?,
            newValue: Translator?
        ) {
            oldValue?.close()
        }
    }

    fun translateIfReady(text: String, sourceTag: String, targetTag: String): String? {
        val srcMl = TranslateLanguage.fromLanguageTag(sourceTag) ?: return null
        val tgtMl = TranslateLanguage.fromLanguageTag(targetTag) ?: return null

        if (!isReady(srcMl, tgtMl)) {
            ensureModelsDownload(srcMl, tgtMl)
            return null
        }

        val key = "$srcMl->$tgtMl"
        val translator = synchronized(translators) {
            translators.get(key) ?: run {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(srcMl)
                    .setTargetLanguage(tgtMl)
                    .build()
                val created = Translation.getClient(options)
                translators.put(key, created)
                created
            }
        }

        return try {
            Tasks.await(translator.translate(text), 2, TimeUnit.SECONDS)
        } catch (e: Throwable) {
            Log.d(TAG, "ML Kit translation execution error: ${e.message}")
            null
        }
    }

    fun getSupportedLanguages(): List<String> {
        return try {
            TranslateLanguage.getAllLanguages().toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun isModelDownloaded(langCode: String): Boolean {
        val tag = TranslateLanguage.fromLanguageTag(langCode) ?: langCode
        return checkModelReady(tag)
    }

    fun downloadModel(langCode: String, onComplete: (Boolean) -> Unit) {
        val tag = TranslateLanguage.fromLanguageTag(langCode) ?: langCode
        if (tag == "en") {
            onComplete(true)
            return
        }
        val model = TranslateRemoteModel.Builder(tag).build()
        val conditions = DownloadConditions.Builder().build()
        RemoteModelManager.getInstance().download(model, conditions)
            .addOnSuccessListener {
                modelReady[tag] = true
                onComplete(true)
            }
            .addOnFailureListener {
                modelReady[tag] = false
                onComplete(false)
            }
    }

    fun deleteModel(langCode: String): Boolean {
        val tag = TranslateLanguage.fromLanguageTag(langCode) ?: langCode
        return try {
            val model = TranslateRemoteModel.Builder(tag).build()
            Tasks.await(RemoteModelManager.getInstance().deleteDownloadedModel(model), 1, TimeUnit.SECONDS)
            modelReady[tag] = false
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun isReady(sourceTag: String, targetTag: String): Boolean {
        val srcReady = sourceTag == "en" || checkModelReady(sourceTag)
        val tgtReady = targetTag == "en" || checkModelReady(targetTag)
        return srcReady && tgtReady
    }

    private fun checkModelReady(tag: String): Boolean {
        if (tag == "en") return true
        modelReady[tag]?.let { return it }

        return try {
            val model = TranslateRemoteModel.Builder(tag).build()
            val downloaded = Tasks.await(
                RemoteModelManager.getInstance().isModelDownloaded(model),
                300,
                TimeUnit.MILLISECONDS
            )
            modelReady[tag] = downloaded
            downloaded
        } catch (_: Throwable) {
            modelReady[tag] = false
            false
        }
    }

    private fun ensureModelsDownload(sourceTag: String, targetTag: String) {
        if (sourceTag != "en") ensureModelDownload(sourceTag)
        if (targetTag != "en") ensureModelDownload(targetTag)
    }

    private fun ensureModelDownload(tag: String) {
        if (tag == "en" || modelReady[tag] == true) return

        val flag = downloading.getOrPut(tag) { AtomicBoolean(false) }
        if (!flag.compareAndSet(false, true)) return

        val model = TranslateRemoteModel.Builder(tag).build()
        val conditions = DownloadConditions.Builder().build()

        RemoteModelManager.getInstance()
            .download(model, conditions)
            .addOnSuccessListener {
                modelReady[tag] = true
                flag.set(false)
                Log.i(TAG, "ML Kit model downloaded: $tag")
            }
            .addOnFailureListener { e ->
                modelReady[tag] = false
                flag.set(false)
                Log.w(TAG, "ML Kit model download failed for $tag: ${e.message}")
            }
    }

    fun cleanup() {
        synchronized(translators) {
            translators.evictAll()
        }
        modelReady.clear()
        downloading.clear()
    }

    companion object {
        private const val TAG = "MlKitTranslatorBridge"
    }
}
