package com.damla.alarms

import kotlin.test.Test
import kotlin.test.assertEquals

class TurkishNumbersTest {
    private fun genitive(number: String) = number + "’" + TurkishNumbers.genitive(number)

    private fun ablative(number: String) = number + "’" + TurkishNumbers.ablative(number)

    @Test
    fun `endings follow the last word the number is read with`() {
        assertEquals(
            listOf("1’in", "2’nin", "3’ün", "4’ün", "5’in", "6’nın", "7’nin", "8’in", "9’un"),
            (1..9).map { genitive(it.toString()) },
        )
        assertEquals(
            listOf("10’un", "20’nin", "30’un", "40’ın", "50’nin", "60’ın", "70’in", "80’in", "90’ın"),
            (1..9).map { genitive((it * 10).toString()) },
        )
        assertEquals(
            listOf("1’den", "2’den", "3’ten", "4’ten", "5’ten", "6’dan", "7’den", "8’den", "9’dan"),
            (1..9).map { ablative(it.toString()) },
        )
        assertEquals(
            listOf("10’dan", "20’den", "30’dan", "40’tan", "50’den", "60’tan", "70’ten", "80’den", "90’dan"),
            (1..9).map { ablative((it * 10).toString()) },
        )
    }

    @Test
    fun `hundreds, thousands and beyond end with their own word`() {
        assertEquals("4.300’ün", genitive("4.300"))
        assertEquals("4.000’in", genitive("4.000"))
        assertEquals("300.000’in", genitive("300.000"))
        assertEquals("2.000.000’un", genitive("2.000.000"))
        assertEquals("5.000.000.000’ın", genitive("5.000.000.000"))
        assertEquals("7.000.000.000.000’un", genitive("7.000.000.000.000"))
        assertEquals("9.000.000.000.000.000’un", genitive("9.000.000.000.000.000"))
        assertEquals("64.125’in", genitive("64.125"))
        assertEquals("100’den", ablative("100"))
    }

    @Test
    fun `decimals are read after the comma, zero decimals are not`() {
        assertEquals("2,5’ten", ablative("2,5"))
        assertEquals("1,50’nin", genitive("1,50"))
        assertEquals("0,05’in", genitive("0,05"))
        assertEquals("4.300,00’ün", genitive("4.300,00"))
        assertEquals("0’ın", genitive("0"))
        assertEquals("0,00’dan", ablative("0,00"))
    }
}
