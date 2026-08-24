// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.translation.plugin

import android.content.Context
import helium314.keyboard.latin.translation.ITranslationProvider

class TranslationProviderImpl : ITranslationProvider {
    private var appContext: Context? = null
    private var initialized = false
    private var mlKitBridge: MlKitTranslatorBridge? = null

    override fun getInterfaceVersion(): Int = 2

    override fun init(context: Context) {
        this.appContext = context.applicationContext
        this.initialized = true
        this.mlKitBridge = try {
            MlKitTranslatorBridge(this.appContext ?: context)
        } catch (e: Throwable) {
            android.util.Log.e("TranslationProviderImpl", "Failed to initialize MlKitTranslatorBridge", e)
            null
        }
    }

    override fun isAvailable(): Boolean = initialized

    override fun translate(text: String, targetLang: String, sourceLang: String): String {
        if (text.isBlank()) return text
        val langCode = mapLanguageToCode(targetLang)
        val sourceCode = if (sourceLang == "auto" || sourceLang.isBlank()) "en" else mapLanguageToCode(sourceLang)

        val mlResult = mlKitBridge?.translateIfReady(text, sourceCode, langCode)
        if (!mlResult.isNullOrBlank()) {
            return mlResult
        }

        throw java.io.IOException("Offline model not installed for $targetLang. Please import the model in Settings.")
    }

    override fun getSupportedLanguages(): List<String> {
        return mlKitBridge?.getSupportedLanguages() ?: emptyList()
    }

    override fun isModelDownloaded(langCode: String): Boolean {
        return mlKitBridge?.isModelDownloaded(langCode) ?: false
    }

    override fun downloadModel(langCode: String, listener: helium314.keyboard.latin.translation.TranslationModelDownloadListener) {
        val bridge = mlKitBridge
        if (bridge == null) {
            android.util.Log.w("TranslationProviderImpl", "mlKitBridge is null during downloadModel")
            listener.onComplete(false, "Bridge not initialized")
            return
        }
        bridge.downloadModel(langCode, listener)
    }

    override fun deleteModel(langCode: String): Boolean {
        return mlKitBridge?.deleteModel(langCode) ?: false
    }

    override fun cleanup() {
        try {
            mlKitBridge?.javaClass?.getMethod("cleanup")?.invoke(mlKitBridge)
        } catch (_: Throwable) {
        }
        mlKitBridge = null
        appContext = null
        initialized = false
    }

    private fun mapLanguageToCode(lang: String): String {
        val clean = lang.trim().lowercase()
        if (clean.isBlank()) return "en"

        val explicitMap = mapOf(
            "english" to "en",
            "spanish" to "es",
            "french" to "fr",
            "german" to "de",
            "italian" to "it",
            "portuguese" to "pt",
            "chinese" to "zh",
            "chinese (simplified)" to "zh",
            "chinese (traditional)" to "zh",
            "japanese" to "ja",
            "korean" to "ko",
            "arabic" to "ar",
            "russian" to "ru",
            "hindi" to "hi",
            "bengali" to "bn",
            "indonesian" to "id",
            "dutch" to "nl",
            "turkish" to "tr",
            "polish" to "pl",
            "ukrainian" to "uk",
            "swedish" to "sv",
            "danish" to "da",
            "norwegian" to "no",
            "finnish" to "fi",
            "greek" to "el",
            "hebrew" to "he",
            "thai" to "th",
            "vietnamese" to "vi",
            "tamil" to "ta",
            "telugu" to "te",
            "marathi" to "mr",
            "gujarati" to "gu",
            "kannada" to "kn",
            "malayalam" to "ml",
            "urdu" to "ur",
            "persian" to "fa",
            "farsi" to "fa",
            "persian (farsi)" to "fa",
            "swahili" to "sw",
            "romanian" to "ro",
            "czech" to "cs",
            "hungarian" to "hu",
            "filipino" to "tl",
            "tagalog" to "tl",
            "filipino (tagalog)" to "tl",
            "malay" to "ms",
            "serbian" to "sr",
            "croatian" to "hr",
            "bulgarian" to "bg",
            "slovak" to "sk",
            "slovenian" to "sl",
            "lithuanian" to "lt",
            "latvian" to "lv",
            "estonian" to "et",
            "catalan" to "ca",
            "basque" to "eu",
            "punjabi" to "pa",
            "esperanto" to "eo",
            "latin" to "la",
            "sanskrit" to "sa"
        )

        explicitMap[clean]?.let { return it }

        if (clean.length in 2..3 && clean.all { it.isLetter() }) {
            return clean
        }

        for (locale in java.util.Locale.getAvailableLocales()) {
            val iso = locale.language
            if (iso.isBlank() || iso.length !in 2..3) continue
            val displayEn = locale.getDisplayLanguage(java.util.Locale.ENGLISH).lowercase()
            val displayNative = locale.displayLanguage.lowercase()
            if (displayEn == clean || displayNative == clean) {
                return iso
            }
        }

        for (locale in java.util.Locale.getAvailableLocales()) {
            val iso = locale.language
            if (iso.isBlank() || iso.length !in 2..3) continue
            val displayEn = locale.getDisplayLanguage(java.util.Locale.ENGLISH).lowercase()
            if (displayEn.startsWith(clean)) {
                return iso
            }
        }

        return clean.take(2)
    }
}
