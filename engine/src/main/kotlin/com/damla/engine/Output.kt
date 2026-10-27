package com.damla.engine

import kotlin.time.Instant

/**
 * What one part shows (docs/02).
 *
 * @property shapeState the state whose shape is drawn. Equal to [state] except while [State.CLOSED], when it
 *   is the last open state.
 * @property net latest measured net change in percent, refreshed even while a change is pending.
 * @property range latest measured range in percent.
 * @property since when the shown state began. For a held change this is when the new state first appeared.
 */
public data class PartState(
    val state: State,
    val shapeState: State,
    val net: Double,
    val range: Double,
    val interval: Interval,
    val since: Instant,
)

public data class AssetStates(
    val drop: PartState,
    val upperRing: PartState,
    val lowerRing: PartState,
)

/** Produced when a shown state changes (docs/02). [at] is the evaluation time that confirmed the change. */
public sealed interface EngineEvent {
    public val asset: String
    public val at: Instant
}

public data class DropStateChanged(
    override val asset: String,
    val from: State,
    val to: State,
    override val at: Instant,
) : EngineEvent

public data class RingStateChanged(
    override val asset: String,
    val ring: Ring,
    val from: State,
    val to: State,
    override val at: Instant,
) : EngineEvent

/** The market closed. Parts turn [State.CLOSED] without part events. */
public data class MarketClosed(
    override val asset: String,
    override val at: Instant,
) : EngineEvent

/** The market reopened. Parts whose state differs from their kept shape also get a part event. */
public data class MarketOpened(
    override val asset: String,
    override val at: Instant,
) : EngineEvent
