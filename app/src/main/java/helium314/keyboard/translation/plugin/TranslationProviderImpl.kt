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

    override fun getInterfaceVersion(): Int = 1

    override fun init(context: Context) {
        this.appContext = context.applicationContext
        this.initialized = true
    }

    override fun isAvailable(): Boolean = initialized

    override fun translate(text: String, targetLang: String, sourceLang: String): String {
        if (text.isBlank()) return text
        val langCode = mapLanguageToCode(targetLang)
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$langCode&dt=t&q=$encodedText"
        
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                val translated = parseGoogleTranslateResponse(responseStr)
                if (translated.isNullOrBlank()) {
                    throw java.io.IOException("Failed to parse translation response")
                }
                return translated
            } else {
                throw java.io.IOException("Translation HTTP error: $responseCode")
            }
        } finally {
            conn.disconnect()
        }
    }

    override fun cleanup() {
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
            val sentencesArray = outerArray.optJSONArray(0) ?: return null
            val sb = StringBuilder()
            for (i in 0 until sentencesArray.length()) {
                val sentence = sentencesArray.optJSONArray(i) ?: continue
                val part = sentence.optString(0, "")
                if (part.isNotEmpty()) {
                    sb.append(part)
                }
            }
            val res = sb.toString()
            if (res.isNotBlank()) res else null
        } catch (e: Throwable) {
            null
        }
    }
}
