package com.damla.alarms

import com.damla.api.Alarm
import com.damla.api.ConditionType
import com.damla.gauge.Haptic
import com.damla.gauge.Shape

/**
 * How a fired alarm feels (docs/05): a state alarm like the turn it waited for, a wave alarm like a turn to
 * waves, price and percent alarms like a reached target. The first condition that held decides.
 */
public fun alarmHaptic(
    alarm: Alarm,
    met: List<Int>,
): Haptic {
    var condition = alarm.conditions.first()
    for (index in met) {
        val held = alarm.conditions.getOrNull(index)
        if (held != null) {
            condition = held
            break
        }
    }
    return when (condition.type) {
        ConditionType.STATE -> stateHaptic(condition.target)
        ConditionType.WAVE -> Haptic.CALMED
        ConditionType.PRICE, ConditionType.PERCENT -> Haptic.TARGET_REACHED
    }
}

private fun stateHaptic(target: String?): Haptic {
    for (shape in Shape.entries) {
        if (shape.name == target) return Haptic.turnedTo(shape)
    }
    return Haptic.TARGET_REACHED
}
