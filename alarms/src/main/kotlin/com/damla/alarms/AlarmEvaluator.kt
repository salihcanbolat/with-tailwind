package com.damla.alarms

import com.damla.api.Alarm
import com.damla.api.AlarmOp
import com.damla.api.AlarmPart
import com.damla.api.Condition
import com.damla.api.ConditionType
import com.damla.api.Frequency
import com.damla.api.Joiner
import com.damla.api.Validity
import com.damla.engine.Measurement
import com.damla.engine.PriceWindow
import com.damla.engine.measure
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * What the backend knows about an alarm's asset at [now]: market status, latest price, the confirmed state of
 * each part, the price windows of the alarm's periods (`15m`, `1h`, `1d`, `1w`) and the asset's stable
 * threshold, which tells a wave from a move.
 */
public data class AlarmInput(
    val now: Instant,
    val marketOpen: Boolean,
    val price: Double?,
    val states: Map<AlarmPart, String>,
    val windows: Map<String, PriceWindow>,
    val stable: Double,
)

/**
 * What an alarm remembers between evaluations.
 *
 * @property met whether the conditions held at the last evaluation.
 * @property lastPrice the price then, to see a price cross its value.
 * @property lastFired when it last rang.
 * @property pending an hourly alarm whose conditions became true within the hour waits to ring.
 * @property done a one-time alarm that rang, or an alarm past its validity.
 */
public data class AlarmMemory(
    val met: Boolean = false,
    val lastPrice: Double? = null,
    val lastFired: Instant? = null,
    val pending: Boolean = false,
    val done: Boolean = false,
)

/** [fired] tells the device to show the alarm; [met] lists the conditions that held. */
public data class AlarmResult(
    val memory: AlarmMemory,
    val fired: Boolean,
    val met: List<Int>,
)

/**
 * Evaluates alarms (docs/07) for the backend. Pure: the caller keeps the returned memory.
 *
 * An alarm rings when its conditions become true, not while they stay true: once, every time, or at most
 * once an hour. While its market is closed an alarm that waits for the market is not evaluated; an alarm past
 * its validity never rings again.
 */
public object AlarmEvaluator {
    public val HOUR: Duration = 1.hours

    public fun evaluate(
        alarm: Alarm,
        memory: AlarmMemory?,
        input: AlarmInput,
    ): AlarmResult {
        val previous = memory ?: AlarmMemory()
        if (previous.done || !alarm.enabled) return AlarmResult(previous, fired = false, met = emptyList())
        if (expired(alarm, input.now)) return AlarmResult(previous.copy(done = true), fired = false, met = emptyList())
        if (alarm.waitMarketClosed && !input.marketOpen) return AlarmResult(previous, fired = false, met = emptyList())

        val results = alarm.conditions.map { holds(it, previous.lastPrice, input) }
        val met = results.indices.filter { results[it] }
        val now = alarm.conditions.isNotEmpty() && if (alarm.joiner == Joiner.AND) results.all { it } else results.any { it }
        val waiting = now && (!previous.met || previous.pending)
        val hourPassed = previous.lastFired == null || input.now - previous.lastFired >= HOUR
        val fired = waiting && (alarm.frequency != Frequency.HOURLY || hourPassed)
        val next =
            AlarmMemory(
                met = now,
                lastPrice = input.price ?: previous.lastPrice,
                lastFired = if (fired) input.now else previous.lastFired,
                pending = waiting && !fired,
                done = fired && alarm.frequency == Frequency.ONCE,
            )
        return AlarmResult(next, fired, if (fired) met else emptyList())
    }

    private fun expired(
        alarm: Alarm,
        now: Instant,
    ): Boolean {
        val length =
            when (alarm.validity) {
                Validity.DAY -> 1.days
                Validity.WEEK -> 7.days
                Validity.FOREVER -> return false
            }
        return now - Instant.parse(alarm.createdAt) > length
    }

    private fun holds(
        condition: Condition,
        lastPrice: Double?,
        input: AlarmInput,
    ): Boolean {
        val value = condition.value ?: return condition.type == ConditionType.STATE && stateHolds(condition, input)
        return when (condition.type) {
            ConditionType.STATE -> stateHolds(condition, input)
            ConditionType.PRICE -> priceHolds(condition.op, value, lastPrice, input.price)
            ConditionType.PERCENT -> percentHolds(condition, value, input)
            ConditionType.WAVE -> waveHolds(condition, value, input)
        }
    }

    private fun stateHolds(
        condition: Condition,
        input: AlarmInput,
    ): Boolean = input.states[condition.part ?: AlarmPart.DROP] == condition.target

    /** Above or below the value; or, for BOTH, crossing it since the last evaluation in either direction. */
    private fun priceHolds(
        op: AlarmOp?,
        value: Double,
        lastPrice: Double?,
        price: Double?,
    ): Boolean {
        if (price == null) return false
        return when (op ?: AlarmOp.UP) {
            AlarmOp.UP -> price > value
            AlarmOp.DOWN -> price < value
            AlarmOp.BOTH -> lastPrice != null && (lastPrice < value) != (price < value)
        }
    }

    private fun percentHolds(
        condition: Condition,
        value: Double,
        input: AlarmInput,
    ): Boolean {
        val measurement = measure(input.windows[condition.interval]) as? Measurement.Valid ?: return false
        return when (condition.op ?: AlarmOp.UP) {
            AlarmOp.UP -> measurement.net >= value
            AlarmOp.DOWN -> measurement.net <= -value
            AlarmOp.BOTH -> abs(measurement.net) >= value
        }
    }

    /** "Iniş çıkış yapıp yerinde kaldı": a range above the value while the net change stays within the stable threshold. */
    private fun waveHolds(
        condition: Condition,
        value: Double,
        input: AlarmInput,
    ): Boolean {
        val measurement = measure(input.windows[condition.interval]) as? Measurement.Valid ?: return false
        return measurement.range > value && abs(measurement.net) < input.stable
    }
}
