package com.damla.api

import kotlinx.serialization.Serializable

/** What a condition watches (docs/07): a part's state, the price, a change in percent, or a wave. */
@Serializable
enum class ConditionType { STATE, PRICE, PERCENT, WAVE }

@Serializable
enum class AlarmPart { DROP, UPPER_RING, LOWER_RING }

/**
 * The direction of a price or percent condition. Price: above, below, crosses the value. Percent: rises,
 * falls, moves either way by the value.
 */
@Serializable
enum class AlarmOp { UP, DOWN, BOTH }

@Serializable
enum class Joiner { AND, OR }

/** Once; every time the conditions become true; at most once an hour. */
@Serializable
enum class Frequency { ONCE, EVERY_TIME, HOURLY }

@Serializable
enum class Validity { DAY, WEEK, FOREVER }

/**
 * One condition of an alarm (docs/07). Which fields matter depends on [type]:
 * - STATE: [part] reaches [target] (`UP`, `DOWN`, `FLAT`, `WAVE`).
 * - PRICE: the price is above, below or crosses [value] ([op]).
 * - PERCENT: within [interval], the price rises, falls or moves by at least [value] percent ([op]).
 * - WAVE: within [interval], the price swings by more than [value] percent but ends near where it started.
 */
@Serializable
data class Condition(
    val type: ConditionType,
    val part: AlarmPart? = null,
    val target: String? = null,
    val op: AlarmOp? = null,
    val value: Double? = null,
    val interval: String? = null,
)

/**
 * An alarm of one device (docs/07). The backend evaluates it; the device only shows the notification.
 * [createdAt] starts the [validity] period.
 */
@Serializable
data class Alarm(
    val id: String,
    val assetId: String,
    val conditions: List<Condition>,
    val joiner: Joiner = Joiner.AND,
    val frequency: Frequency = Frequency.EVERY_TIME,
    val validity: Validity = Validity.FOREVER,
    val sound: Boolean = true,
    val waitQuietHours: Boolean = false,
    val waitMarketClosed: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: String,
) {
    companion object {
        const val MAX_CONDITIONS = 3

        /** The periods a percent or wave condition can look back over (design/AlarmKur.dc.html). */
        val PERIODS = listOf("15m", "1h", "1d", "1w")
    }
}
