package com.damla.alarms

import com.damla.api.Alarm
import com.damla.api.AlarmOp
import com.damla.api.AlarmPart
import com.damla.api.Condition
import com.damla.api.ConditionType
import com.damla.api.Frequency
import com.damla.api.Joiner
import com.damla.api.Validity
import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmTextTest {
    private val text = AppStrings.text

    private fun alarm(
        vararg conditions: Condition,
        joiner: Joiner = Joiner.AND,
        frequency: Frequency = Frequency.EVERY_TIME,
        validity: Validity = Validity.FOREVER,
    ) = Alarm("a", "xau_gram", conditions.toList(), joiner, frequency, validity, createdAt = "2026-10-27T10:00:00Z")

    @Test
    fun `every template the sentences use is in the app's strings`() {
        AlarmText.KEYS.forEach { AppStrings.words.template(it) }
    }

    @Test
    fun `the prototype's default sentence`() {
        val usdFlat = alarm(Condition(ConditionType.STATE, AlarmPart.DROP, "UP"))

        assertEquals(
            "USD/TRY damlası yükselişe döndüğünde haber vereceğim. Her seferinde çalar, süresiz.",
            text.summary(usdFlat, "USD/TRY", "₺"),
        )
    }

    @Test
    fun `state conditions name the part and the state`() {
        val rings =
            alarm(
                Condition(ConditionType.STATE, AlarmPart.UPPER_RING, "DOWN"),
                Condition(ConditionType.STATE, AlarmPart.LOWER_RING, "FLAT"),
                Condition(ConditionType.STATE, target = "WAVE"),
                Condition(ConditionType.STATE),
                joiner = Joiner.OR,
                frequency = Frequency.ONCE,
                validity = Validity.DAY,
            )

        assertEquals(
            "Gümüş üst halkası düşüşe döndüğünde ya da alt halkası sabite döndüğünde ya da damlası dalgalıya döndüğünde " +
                "ya da damlası yükselişe döndüğünde haber vereceğim. Bir kez çalar, 1 gün geçerli.",
            text.summary(rings, "Gümüş", "₺"),
        )
    }

    @Test
    fun `prices take the ending their number is read with`() {
        val prices =
            alarm(
                Condition(ConditionType.PRICE, op = AlarmOp.UP, value = 4300.0),
                Condition(ConditionType.PRICE, op = AlarmOp.DOWN, value = 4250.5),
                Condition(ConditionType.PRICE, op = AlarmOp.BOTH, value = 4000.0),
                frequency = Frequency.HOURLY,
                validity = Validity.WEEK,
            )

        assertEquals(
            "Gram altın fiyatı ₺4.300’ün üzerine çıktığında ve fiyatı ₺4.250,5’in altına indiğinde ve " +
                "fiyatı ₺4.000 seviyesini geçtiğinde haber vereceğim. Saatte en fazla bir kez çalar, 1 hafta geçerli.",
            text.summary(prices, "Gram altın", "₺"),
        )
    }

    @Test
    fun `percent and wave conditions read their period`() {
        val moves =
            alarm(
                Condition(ConditionType.PERCENT, op = AlarmOp.UP, value = 2.5, interval = "1h"),
                Condition(ConditionType.PERCENT, op = AlarmOp.DOWN, value = 3.0, interval = "1d"),
                Condition(ConditionType.PERCENT, value = 1.0, op = AlarmOp.BOTH, interval = "15m"),
            )
        val wave = alarm(Condition(ConditionType.WAVE, value = 4.0, interval = "1w"))

        assertEquals(
            "Bitcoin 1 saat içinde %2,5 yükseldiğinde ve 1 gün içinde %3 düştüğünde ve 15 dk içinde %1 oynadığında " +
                "haber vereceğim. Her seferinde çalar, süresiz.",
            text.summary(moves, "Bitcoin", "$"),
        )
        assertEquals(
            "Bitcoin 1 hafta içinde %4’ten fazla iniş çıkış yapıp yerinde kaldığında haber vereceğim. Her seferinde çalar, süresiz.",
            text.summary(wave, "Bitcoin", "$"),
        )
    }

    @Test
    fun `a value not typed yet reads as an ellipsis without an ending`() {
        val unfinished =
            alarm(
                Condition(ConditionType.PRICE),
                Condition(ConditionType.WAVE),
            )

        assertEquals(
            "Altın fiyatı ₺… üzerine çıktığında ve 1 saat içinde %… fazla iniş çıkış yapıp yerinde kaldığında " +
                "haber vereceğim. Her seferinde çalar, süresiz.",
            text.summary(unfinished, "Altın", "₺"),
        )
    }

    @Test
    fun `a rung alarm tells what happened`() {
        val alarm =
            alarm(
                Condition(ConditionType.PRICE, op = AlarmOp.UP, value = 4300.0),
                Condition(ConditionType.STATE, AlarmPart.DROP, "UP"),
                Condition(ConditionType.WAVE, value = 2.0, interval = "1d"),
                joiner = Joiner.OR,
            )

        assertEquals("Gram altın fiyatı ₺4.300’ün üzerine çıktı", text.fired(alarm, "Gram altın", "₺", listOf(0)))
        assertEquals(
            "Gram altın damlası yükselişe döndü ya da 1 gün içinde %2’den fazla iniş çıkış yapıp yerinde kaldı",
            text.fired(alarm, "Gram altın", "₺", listOf(1, 2)),
        )
        assertEquals(
            "Gram altın fiyatı ₺4.300’ün üzerine çıktı ya da damlası yükselişe döndü ya da " +
                "1 gün içinde %2’den fazla iniş çıkış yapıp yerinde kaldı",
            text.fired(alarm, "Gram altın", "₺", emptyList()),
        )
    }

    @Test
    fun `past tense of every kind`() {
        val all =
            alarm(
                Condition(ConditionType.PRICE, op = AlarmOp.DOWN, value = 41.0),
                Condition(ConditionType.PRICE, op = AlarmOp.BOTH, value = 42.0),
                Condition(ConditionType.PERCENT, op = AlarmOp.UP, value = 1.0, interval = "1h"),
            )
        val falls =
            alarm(
                Condition(ConditionType.PERCENT, op = AlarmOp.DOWN, value = 6.0, interval = "1d"),
                Condition(ConditionType.PERCENT, op = AlarmOp.BOTH, value = 0.5, interval = "15m"),
            )

        assertEquals(
            "USD/TRY fiyatı ₺41’in altına indi ve fiyatı ₺42 seviyesini geçti ve 1 saat içinde %1 yükseldi",
            text.fired(all, "USD/TRY", "₺", listOf(0, 1, 2)),
        )
        assertEquals("USD/TRY 1 gün içinde %6 düştü ve 15 dk içinde %0,5 oynadı", text.fired(falls, "USD/TRY", "₺", listOf(0, 1)))
    }
}
