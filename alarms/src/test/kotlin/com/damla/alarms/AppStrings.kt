package com.damla.alarms

import java.io.File
import java.text.NumberFormat
import java.util.Locale

/** The app's own `alarm_*` templates from strings.xml, so the tests check the sentences users will read. */
object AppStrings {
    private val strings: Map<String, String> by lazy {
        val file = File(checkNotNull(System.getProperty("damla.appStrings")) { "damla.appStrings is not set" })
        Regex("""<string name="([^"]+)">(.*?)</string>""")
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'").replace("\\\"", "\"") }
    }

    val words = AlarmWords { key -> checkNotNull(strings[key]) { "strings.xml has no $key" } }

    fun number(value: Double): String =
        NumberFormat
            .getNumberInstance(Locale.forLanguageTag("tr-TR"))
            .apply { maximumFractionDigits = 2 }
            .format(value)

    val text = AlarmText(words, ::number)
}
