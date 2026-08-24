// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.translation.plugin

import android.content.Context
import helium314.keyboard.latin.translation.ITranslationProvider
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray

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

        val prefs = appContext?.let { android.preference.PreferenceManager.getDefaultSharedPreferences(it) }
            ?: appContext?.getSharedPreferences(appContext?.packageName + "_preferences", Context.MODE_PRIVATE)
        val mode = prefs?.getString("pref_translation_mode", "auto") ?: "auto"

        // 1. Tier 1: Try on-device ML Kit if allowed by mode
        if (mode != "online_only") {
            val mlResult = mlKitBridge?.translateIfReady(text, sourceCode, langCode)
            if (!mlResult.isNullOrBlank()) {
                return mlResult
            }
            if (mode == "offline_only") {
                throw java.io.IOException("Offline model not downloaded. Download in Settings.")
            }
        }

        // 2. Tier 2: Official Google Chrome Extension translation endpoint (clean, fast, avoids 429 rate limit)
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val urlStr = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=$sourceLang&tl=$langCode&q=$encodedText"
        val result = executeRequest(urlStr)
        if (!result.isNullOrBlank()) {
            return result
        }

        throw java.io.IOException("Translation returned empty result")
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

    private fun executeRequest(urlStr: String): String? {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept", "*/*")
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                return parseGoogleTranslateResponse(responseStr)
            } else {
                throw java.io.IOException("HTTP error $responseCode from $urlStr")
            }
        } finally {
            conn.disconnect()
        }
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
            "chinese" to "zh-CN",
            "chinese (simplified)" to "zh-CN",
            "chinese (traditional)" to "zh-TW",
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

        // 1. If 2-3 letter ISO code, use directly
        if (clean.length in 2..3 && clean.all { it.isLetter() }) {
            return clean
        }

        // 2. Exact match against Locales
        for (locale in java.util.Locale.getAvailableLocales()) {
            val iso = locale.language
            if (iso.isBlank() || iso.length !in 2..3) continue

            val displayEn = locale.getDisplayLanguage(java.util.Locale.ENGLISH).lowercase()
            val displayNative = locale.displayLanguage.lowercase()

            if (displayEn == clean || displayNative == clean) {
                return iso
            }
        }

        // 3. Prefix match against Locales
        for (locale in java.util.Locale.getAvailableLocales()) {
            val iso = locale.language
            if (iso.isBlank() || iso.length !in 2..3) continue

            val displayEn = locale.getDisplayLanguage(java.util.Locale.ENGLISH).lowercase()
            if (displayEn.startsWith(clean)) {
                return iso
            }
        }

        // 4. Substring match against Locales
        for (locale in java.util.Locale.getAvailableLocales()) {
            val iso = locale.language
            if (iso.isBlank() || iso.length !in 2..3) continue

            val displayEn = locale.getDisplayLanguage(java.util.Locale.ENGLISH).lowercase()
            if (displayEn.contains(clean)) {
                return iso
            }
        }

        return clean.take(2)
    }

    private fun parseGoogleTranslateResponse(jsonStr: String): String? {
        return try {
            val outerArray = JSONArray(jsonStr)
            if (outerArray.length() == 0) return null

            val firstElem = outerArray.opt(0)
            if (firstElem is JSONArray) {
                // Format 1 (dict-chrome-ex): [["translated text", "detected_lang"]]
                if (firstElem.length() > 0 && firstElem.opt(0) is String) {
                    val candidate = firstElem.optString(0, "")
                    if (candidate.isNotBlank()) return candidate
                }
                // Format 2 (gtx): [[["part1", "orig1", ...], ["part2", "orig2", ...]], null, "en"]
                val sb = StringBuilder()
                for (i in 0 until firstElem.length()) {
                    val sentence = firstElem.optJSONArray(i) ?: continue
                    val part = sentence.optString(0, "")
                    if (part.isNotEmpty()) {
                        sb.append(part)
                    }
                }
                val res = sb.toString()
                if (res.isNotBlank()) return res
            } else if (firstElem is String) {
                return firstElem
            }
            null
        } catch (e: Throwable) {
            null
        }
    }
}
