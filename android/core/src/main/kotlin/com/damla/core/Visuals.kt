package com.damla.core

import com.damla.api.AssetSnapshot
import com.damla.api.PartSnapshot
import com.damla.api.SnapshotResponse
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

/**
 * The assets of [ids] that can be drawn (the snapshot has states for all three parts), in order. The widget
 * and the overlay both use it, so the overlay's first cells are exactly the widget's drops.
 */
fun SnapshotResponse?.shownAssets(
    ids: List<String>,
    settings: AppSettings,
): List<AssetSnapshot> {
    val byId = this?.assets.orEmpty().associateBy { it.id }
    return ids.mapNotNull { id -> byId[id]?.takeIf { it.visual(settings) != null } }
}

/** The small coloured badge before a name in the white box (design/Prototip.dc.html), as 0xAARRGGBB. */
data class LabelBadge(
    val background: Int,
    val foreground: Int,
    val symbol: String,
)

/** Badge colours of the designs; assets the designs do not show get a neutral grey. */
fun labelBadge(badge: String): LabelBadge =
    when (badge) {
        "au" -> LabelBadge(0xFFF2C230.toInt(), 0xFF5A4300.toInt(), "Au")
        "usd" -> LabelBadge(0xFF2E7D4F.toInt(), WHITE, "$")
        "ag" -> LabelBadge(0xFFC7CCD3.toInt(), 0xFF3A4048.toInt(), "Ag")
        "btc" -> LabelBadge(0xFFF7931A.toInt(), WHITE, "₿")
        "eur" -> LabelBadge(0xFF003399.toInt(), 0xFFFFCC00.toInt(), "€")
        else -> LabelBadge(0xFFE5E5EA.toInt(), DARK_TEXT, badge.take(2).uppercase())
    }

private const val WHITE = 0xFFFFFFFF.toInt()
private const val DARK_TEXT = 0xFF1C1C1E.toInt()
