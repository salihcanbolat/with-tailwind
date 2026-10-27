package com.damla.alarms

import com.damla.api.Alarm
import com.damla.api.AlarmOp
import com.damla.api.AlarmPart
import com.damla.api.Condition
import com.damla.api.ConditionType
import com.damla.api.Frequency
import com.damla.api.Joiner
import com.damla.api.Validity

/**
 * The sentence templates, by name. The apps keep them as `alarm_*` string resources and hand them in, so the
 * words stay out of the code; only Turkish grammar (number endings) lives here.
 */
public fun interface AlarmWords {
    public fun template(key: String): String
}

/**
 * The alarm's plain Turkish sentences (docs/07): the summary under the alarm form, rewritten on every choice,
 * and the notification text when it rings. [number] writes a value the Turkish way ("4.300", "2,5").
 */
public class AlarmText(
    private val words: AlarmWords,
    private val number: (Double) -> String,
) {
    /** "Gram altın fiyatı ₺4.300’ün üzerine çıktığında haber vereceğim. Her seferinde çalar, süresiz." */
    public fun summary(
        alarm: Alarm,
        assetName: String,
        unit: String,
    ): String =
        words.template("alarm_summary").format(
            assetName,
            join(alarm.conditions.map { phrase(it, unit, Tense.WHEN) }, alarm.joiner),
            words.template(FREQUENCY.getValue(alarm.frequency)),
            words.template(VALIDITY.getValue(alarm.validity)),
        )

    /** "Gram altın fiyatı ₺4.300’ün üzerine çıktı": the conditions that were met when the alarm rang. */
    public fun fired(
        alarm: Alarm,
        assetName: String,
        unit: String,
        met: List<Int>,
    ): String {
        val shown = met.mapNotNull { alarm.conditions.getOrNull(it) }.ifEmpty { alarm.conditions }
        return words.template("alarm_fired").format(assetName, join(shown.map { phrase(it, unit, Tense.DONE) }, alarm.joiner))
    }

    private enum class Tense(
        val prefix: String,
    ) {
        WHEN("alarm_cond"),
        DONE("alarm_fired"),
    }

    private fun join(
        phrases: List<String>,
        joiner: Joiner,
    ): String = phrases.joinToString(" " + words.template(if (joiner == Joiner.AND) "alarm_and" else "alarm_or") + " ")

    private fun phrase(
        condition: Condition,
        unit: String,
        tense: Tense,
    ): String {
        val value = condition.value
        val text = if (value == null) words.template("alarm_value_missing") else number(value)
        val op = OPS.getValue(condition.op ?: AlarmOp.UP)
        val period = words.template("alarm_period_" + (condition.interval ?: DEFAULT_PERIOD))

        fun template(key: String) = words.template(key)
        return when (condition.type) {
            ConditionType.STATE -> {
                template("${tense.prefix}_state").format(
                    template(PARTS.getValue(condition.part ?: AlarmPart.DROP)),
                    template("alarm_target_" + (condition.target ?: "UP").lowercase()),
                )
            }

            ConditionType.PRICE -> {
                template("${tense.prefix}_price_$op").format(unit + text, value?.let { "’" + TurkishNumbers.genitive(text) }.orEmpty())
            }

            ConditionType.PERCENT -> {
                template("${tense.prefix}_percent_$op").format(period, text)
            }

            ConditionType.WAVE -> {
                template("${tense.prefix}_wave").format(period, text, value?.let { "’" + TurkishNumbers.ablative(text) }.orEmpty())
            }
        }
    }

    public companion object {
        private const val DEFAULT_PERIOD = "1h"
        private val OPS = mapOf(AlarmOp.UP to "up", AlarmOp.DOWN to "down", AlarmOp.BOTH to "both")
        private val PARTS =
            mapOf(
                AlarmPart.DROP to "alarm_part_drop",
                AlarmPart.UPPER_RING to "alarm_part_upper_ring",
                AlarmPart.LOWER_RING to "alarm_part_lower_ring",
            )
        private val FREQUENCY =
            mapOf(
                Frequency.ONCE to "alarm_frequency_once",
                Frequency.EVERY_TIME to "alarm_frequency_every_time",
                Frequency.HOURLY to "alarm_frequency_hourly",
            )
        private val VALIDITY =
            mapOf(
                Validity.DAY to "alarm_validity_day",
                Validity.WEEK to "alarm_validity_week",
                Validity.FOREVER to "alarm_validity_forever",
            )

        /** Every template the sentences use. */
        public val KEYS: List<String> =
            listOf("alarm_summary", "alarm_fired", "alarm_and", "alarm_or", "alarm_value_missing") +
                listOf("alarm_cond", "alarm_fired").flatMap { prefix ->
                    listOf("${prefix}_state", "${prefix}_wave") +
                        OPS.values.flatMap { listOf("${prefix}_price_$it", "${prefix}_percent_$it") }
                } + PARTS.values + listOf("up", "down", "flat", "wave").map { "alarm_target_$it" } +
                Alarm.PERIODS.map { "alarm_period_$it" } + FREQUENCY.values + VALIDITY.values
    }
}
