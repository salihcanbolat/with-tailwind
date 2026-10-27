package com.damla.engine

import kotlin.time.Duration
import kotlin.time.Instant

public data class PartConfig(
    val interval: Interval,
    val thresholds: Thresholds,
)

/** The user's settings for one asset. Cross rates take their thresholds from [CrossPair.category]. */
public data class AssetConfig(
    val asset: String,
    val drop: PartConfig,
    val upperRing: PartConfig,
    val lowerRing: PartConfig,
)

/** Market status and the price window of each part at [now]. A null window means the data is missing. */
public data class AssetInput(
    val now: Instant,
    val marketOpen: Boolean,
    val drop: PriceWindow?,
    val upperRing: PriceWindow?,
    val lowerRing: PriceWindow?,
)

/** A state that differs from the shown one and has been seen continuously since [since]. */
public data class Pending(
    val state: State,
    val since: Instant,
)

/**
 * What the engine remembers about one part between evaluations.
 *
 * @property confirmed the shown state.
 * @property raw the latest classification, which hysteresis is applied against.
 * @property pending a different state waiting out its hold time.
 */
public data class PartMemory(
    val confirmed: PartState,
    val raw: State,
    val pending: Pending?,
)

/** Everything the engine remembers about one asset. It is immutable and can be stored as is. */
public data class AssetMemory(
    val marketOpen: Boolean,
    val drop: PartMemory?,
    val upperRing: PartMemory?,
    val lowerRing: PartMemory?,
)

/** [states] is null until every part has had data once. */
public data class Evaluation(
    val memory: AssetMemory,
    val states: AssetStates?,
    val events: List<EngineEvent>,
)

/**
 * Computes the gauge states of docs/02. Pure: the caller passes the previous [AssetMemory] in and keeps
 * the one returned.
 */
public class GaugeEngine(
    private val config: EngineConfig = EngineConfig(),
) {
    public fun evaluate(
        asset: AssetConfig,
        memory: AssetMemory?,
        input: AssetInput,
    ): Evaluation {
        val now = input.now
        val open = input.marketOpen
        val drop = step(memory?.drop, input.drop, asset.drop, open, now, config.dropHold)
        val upper = step(memory?.upperRing, input.upperRing, asset.upperRing, open, now, config.ringHold)
        val lower = step(memory?.lowerRing, input.lowerRing, asset.lowerRing, open, now, config.ringHold)

        val wasOpen = memory?.marketOpen
        val events =
            buildList {
                if (wasOpen == true && !open) add(MarketClosed(asset.asset, now))
                if (wasOpen == false && open) add(MarketOpened(asset.asset, now))
                drop.change?.let { add(DropStateChanged(asset.asset, it.from, it.to, now)) }
                upper.change?.let { add(RingStateChanged(asset.asset, Ring.UPPER, it.from, it.to, now)) }
                lower.change?.let { add(RingStateChanged(asset.asset, Ring.LOWER, it.from, it.to, now)) }
            }
        val states =
            if (drop.memory != null && upper.memory != null && lower.memory != null) {
                AssetStates(drop.memory.confirmed, upper.memory.confirmed, lower.memory.confirmed)
            } else {
                null
            }
        return Evaluation(AssetMemory(open, drop.memory, upper.memory, lower.memory), states, events)
    }

    private fun step(
        memory: PartMemory?,
        window: PriceWindow?,
        part: PartConfig,
        marketOpen: Boolean,
        now: Instant,
        hold: Duration,
    ): PartStep {
        val measurement = measure(window)
        if (!marketOpen) return PartStep(close(memory, measurement, part, now))
        if (measurement !is Measurement.Valid) return PartStep(keep(memory, now))

        val (net, range) = measurement
        // After a closure, hysteresis is applied against the shape kept while closed.
        val reference = memory?.let { if (it.confirmed.state == State.CLOSED) it.confirmed.shapeState else it.raw }
        val raw = classifyOpen(net, range, part.thresholds, reference, config.hysteresis)

        fun confirm(since: Instant) = PartMemory(PartState(raw, raw, net, range, part.interval, since), raw, pending = null)

        if (memory == null) return PartStep(confirm(since = now))
        val shown = memory.confirmed
        return when {
            // A reopening market is shown at once; only a different shape counts as a change.
            shown.state == State.CLOSED -> {
                PartStep(confirm(since = now), changeOrNull(shown.shapeState, raw))
            }

            raw == shown.state -> {
                PartStep(PartMemory(shown.copy(net = net, range = range), raw, pending = null))
            }

            else -> {
                val pending = memory.pending?.takeIf { it.state == raw } ?: Pending(raw, since = now)
                if (now - pending.since >= hold) {
                    PartStep(confirm(since = pending.since), Change(shown.state, raw))
                } else {
                    PartStep(PartMemory(shown.copy(net = net, range = range), raw, pending))
                }
            }
        }
    }

    /** Closing needs no data and no hold time; the last open shape is kept and nothing is reported. */
    private fun close(
        memory: PartMemory?,
        measurement: Measurement,
        part: PartConfig,
        now: Instant,
    ): PartMemory? {
        val valid = measurement as? Measurement.Valid
        if (memory == null) {
            if (valid == null) return null
            val shape = classifyOpen(valid.net, valid.range, part.thresholds, previous = null, config.hysteresis)
            return PartMemory(PartState(State.CLOSED, shape, valid.net, valid.range, part.interval, now), shape, pending = null)
        }
        val shown = memory.confirmed
        val closed = if (shown.state == State.CLOSED) shown else shown.copy(state = State.CLOSED, since = now)
        val refreshed = if (valid == null) closed else closed.copy(net = valid.net, range = valid.range)
        return PartMemory(refreshed, raw = shown.shapeState, pending = null)
    }

    /** Without usable data nothing changes, except that a reopened market shows the kept shape again. */
    private fun keep(
        memory: PartMemory?,
        now: Instant,
    ): PartMemory? {
        if (memory == null || memory.confirmed.state != State.CLOSED) return memory
        val shown = memory.confirmed
        return memory.copy(confirmed = shown.copy(state = shown.shapeState, since = now))
    }

    private fun changeOrNull(
        from: State,
        to: State,
    ): Change? = if (from == to) null else Change(from, to)

    private class PartStep(
        val memory: PartMemory?,
        val change: Change? = null,
    )

    private class Change(
        val from: State,
        val to: State,
    )
}
