package com.damla.alarms

import com.damla.api.Alarm
import com.damla.api.ConditionType
import kotlin.time.Instant

/** What makes an alarm complete (docs/07). The app enables "Kaydet" with it; the backend refuses others. */
public object AlarmValidation {
    public const val MAX_ID_LENGTH: Int = 64
    private val STATES = setOf("UP", "DOWN", "FLAT", "WAVE")

    /** Problems in English for the API; empty when the alarm can be saved. */
    public fun problems(alarm: Alarm): List<String> =
        buildList {
            if (alarm.id.isBlank() || alarm.id.length > MAX_ID_LENGTH) add("alarm id must be 1 to $MAX_ID_LENGTH characters")
            if (alarm.conditions.isEmpty() || alarm.conditions.size > Alarm.MAX_CONDITIONS) {
                add("${alarm.id}: 1 to ${Alarm.MAX_CONDITIONS} conditions")
            }
            if (runCatching { Instant.parse(alarm.createdAt) }.isFailure) add("${alarm.id}: invalid createdAt '${alarm.createdAt}'")
            alarm.conditions.forEachIndexed { i, condition ->
                val name = "${alarm.id}.conditions[$i]"
                val value = condition.value
                when (condition.type) {
                    ConditionType.STATE -> {
                        if (condition.target !in STATES) add("$name: target must be one of $STATES")
                    }

                    else -> {
                        if (value == null || !value.isFinite() || value <= 0) add("$name: value must be a positive number")
                        if (condition.type != ConditionType.PRICE && condition.interval !in Alarm.PERIODS) {
                            add("$name: interval must be one of ${Alarm.PERIODS}")
                        }
                    }
                }
            }
        }
}
