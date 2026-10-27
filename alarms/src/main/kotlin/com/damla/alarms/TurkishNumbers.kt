package com.damla.alarms

/**
 * Case endings after a number written in Turkish style ("4.300", "2,5"). Turkish suffixes follow the last
 * word the number is read with: "4.300" is "dört bin üç yüz", so "4.300’ün"; "2,5" is "iki virgül beş", so
 * "2,5’ten".
 */
public object TurkishNumbers {
    /** The genitive ending without the apostrophe: "in", "ın", "un", "ün", "nin", "nın". */
    public fun genitive(number: String): String = lastWord(number).genitive

    /** The ablative ending without the apostrophe: "den", "dan", "ten", "tan". */
    public fun ablative(number: String): String = lastWord(number).ablative

    internal enum class Word(
        val genitive: String,
        val ablative: String,
    ) {
        SIFIR("ın", "dan"),
        BIR("in", "den"),
        IKI("nin", "den"),
        UC("ün", "ten"),
        DORT("ün", "ten"),
        BES("in", "ten"),
        ALTI("nın", "dan"),
        YEDI("nin", "den"),
        SEKIZ("in", "den"),
        DOKUZ("un", "dan"),
        ON("un", "dan"),
        YIRMI("nin", "den"),
        OTUZ("un", "dan"),
        KIRK("ın", "tan"),
        ELLI("nin", "den"),
        ALTMIS("ın", "tan"),
        YETMIS("in", "ten"),
        SEKSEN("in", "den"),
        DOKSAN("ın", "dan"),
        YUZ("ün", "den"),
        BIN("in", "den"),
        MILYON("un", "dan"),
        MILYAR("ın", "dan"),
        TRILYON("un", "dan"),
    }

    /**
     * The word a number ends with when read aloud. Decimals are read as a whole number after "virgül"
     * ("1,50" is "bir virgül elli"); zero decimals are not read.
     */
    internal fun lastWord(number: String): Word {
        val digits = number.filter { it.isDigit() || it == ',' }
        val integer = digits.substringBefore(',')
        val decimals = digits.substringAfter(',', "")
        return if (decimals.any { it != '0' }) wordOf(decimals) else wordOf(integer)
    }

    private val UNITS = listOf(Word.SIFIR, Word.BIR, Word.IKI, Word.UC, Word.DORT, Word.BES, Word.ALTI, Word.YEDI, Word.SEKIZ, Word.DOKUZ)
    private val TENS =
        listOf(Word.SIFIR, Word.ON, Word.YIRMI, Word.OTUZ, Word.KIRK, Word.ELLI, Word.ALTMIS, Word.YETMIS, Word.SEKSEN, Word.DOKSAN)
    private val GROUPS = listOf(Word.BIN, Word.MILYON, Word.MILYAR, Word.TRILYON)

    private fun wordOf(digits: String): Word {
        val significant = digits.trimStart('0')
        // The position, from the right, of the last digit that is not zero.
        val position = significant.length - 1 - significant.indexOfLast { it != '0' }
        val digit = significant.getOrNull(significant.length - 1 - position)?.digitToInt() ?: 0
        return when {
            significant.isEmpty() -> Word.SIFIR
            position == 0 -> UNITS[digit]
            position == 1 -> TENS[digit]
            position == 2 -> Word.YUZ
            else -> GROUPS[minOf(position / 3 - 1, GROUPS.size - 1)]
        }
    }
}
