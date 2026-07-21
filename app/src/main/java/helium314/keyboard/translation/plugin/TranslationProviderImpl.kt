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
        return try {
            val langCode = mapLanguageToCode(targetLang)
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$langCode&dt=t&q=$encodedText"
            
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()

            if (conn.responseCode == 200) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                parseGoogleTranslateResponse(responseStr) ?: text
            } else {
                text
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            text
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
            "malay" to "ms",
            "malayalam" to "ml",
            "hindi" to "hi",
            "tamil" to "ta",
            "telugu" to "te",
            "kannada" to "kn",
            "bengali" to "bn",
            "marathi" to "mr",
            "gujarati" to "gu",
            "punjabi" to "pa",
            "spanish" to "es",
            "french" to "fr",
            "german" to "de",
            "chinese" to "zh-CN",
            "japanese" to "ja",
            "korean" to "ko",
            "russian" to "ru",
            "italian" to "it",
            "portuguese" to "pt",
            "arabic" to "ar",
            "turkish" to "tr",
            "dutch" to "nl",
            "polish" to "pl",
            "swedish" to "sv",
            "greek" to "el",
            "hebrew" to "he",
            "thai" to "th",
            "vietnamese" to "vi",
            "indonesian" to "id",
            "tagalog" to "tl",
            "esperanto" to "eo",
            "latin" to "la",
            "sanskrit" to "sa",
            "swahili" to "sw"
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
            val sentencesArray = outerArray.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until sentencesArray.length()) {
                val sentence = sentencesArray.getJSONArray(i)
                sb.append(sentence.getString(0))
            }
            sb.toString()
        } catch (e: Throwable) {
            null
        }
    }
}
