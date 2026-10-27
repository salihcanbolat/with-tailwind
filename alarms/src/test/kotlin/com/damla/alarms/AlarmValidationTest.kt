package com.damla.alarms

import com.damla.api.Alarm
import com.damla.api.AlarmOp
import com.damla.api.Condition
import com.damla.api.ConditionType
import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmValidationTest {
    private fun alarm(
        vararg conditions: Condition,
        id: String = "a1",
        createdAt: String = "2026-10-27T10:00:00Z",
    ) = Alarm(id, "xau_gram", conditions.toList(), createdAt = createdAt)

    @Test
    fun `a complete alarm has no problems`() {
        val complete =
            alarm(
                Condition(ConditionType.STATE, target = "UP"),
                Condition(ConditionType.PRICE, op = AlarmOp.UP, value = 4300.0),
                Condition(ConditionType.WAVE, value = 2.0, interval = "1d"),
            )

        assertEquals(emptyList(), AlarmValidation.problems(complete))
    }

    @Test
    fun `each problem is named`() {
        val broken =
            alarm(
                Condition(ConditionType.STATE, target = "CLOSED"),
                Condition(ConditionType.PRICE, value = -1.0),
                Condition(ConditionType.PERCENT, value = Double.NaN, interval = "2h"),
                Condition(ConditionType.WAVE),
                id = "",
                createdAt = "yesterday",
            )

        assertEquals(
            listOf(
                "alarm id must be 1 to 64 characters",
                ": 1 to 3 conditions",
                ": invalid createdAt 'yesterday'",
                ".conditions[0]: target must be one of [UP, DOWN, FLAT, WAVE]",
                ".conditions[1]: value must be a positive number",
                ".conditions[2]: value must be a positive number",
                ".conditions[2]: interval must be one of [15m, 1h, 1d, 1w]",
                ".conditions[3]: value must be a positive number",
                ".conditions[3]: interval must be one of [15m, 1h, 1d, 1w]",
            ),
            AlarmValidation.problems(broken),
        )
        assertEquals(
            listOf("alarm id must be 1 to 64 characters", "${"x".repeat(65)}: 1 to 3 conditions"),
            AlarmValidation.problems(alarm(id = "x".repeat(65))),
        )
        assertEquals(
            listOf("a1.conditions[0]: value must be a positive number"),
            AlarmValidation.problems(alarm(Condition(ConditionType.PRICE, value = Double.POSITIVE_INFINITY))),
        )
    }
}
