// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translation

import android.content.Context

interface ITranslationProvider {
    fun getInterfaceVersion(): Int = 1
    fun init(context: Context)
    fun translate(text: String, targetLang: String, sourceLang: String = "auto"): String
    fun isAvailable(): Boolean
    fun cleanup()
}
