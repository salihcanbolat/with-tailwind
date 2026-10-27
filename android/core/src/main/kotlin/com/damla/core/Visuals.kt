package com.damla.core

import com.damla.api.AssetSnapshot
import com.damla.api.PartSnapshot
import com.damla.gauge.GaugeVisual
import com.damla.gauge.Logo
import com.damla.gauge.Shape

/**
 * What a gauge shows for an asset of the snapshot. Only maps the engine's output; it never decides a state
 * (CLAUDE.md). Null until the backend has states for all three parts.
 */
fun AssetSnapshot.visual(
    settings: AppSettings,
    pulseTurns: Int = 0,
): GaugeVisual? {
    val drop = drop ?: return null
    val upper = upperRing ?: return null
    val lower = lowerRing ?: return null
    return GaugeVisual(
        shape = drop.shape(),
        upperRing = upper.shape(),
        lowerRing = lower.shape(),
        closed = drop.state == CLOSED && settings.grayWhenClosed,
        pulseTurns = pulseTurns,
    )
}

/** True while the market of this asset is closed, whatever the grey setting says. */
val AssetSnapshot.closed: Boolean get() = drop?.state == CLOSED

fun AssetSnapshot.logo(): Logo = Logo.forBadge(badge)

/** The shape of a part: its own state, or while closed the last open one (engine's `shapeState`). */
fun PartSnapshot.shape(): Shape = Shape.valueOf(shapeState)

private const val CLOSED = "CLOSED"
