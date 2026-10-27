package com.damla.gauge.android

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.damla.gauge.GaugeLayout
import com.damla.gauge.GaugeMotion
import com.damla.gauge.GaugePainter
import com.damla.gauge.GaugeVisual
import com.damla.gauge.Geometry
import com.damla.gauge.Logo

/**
 * The Damla gauge (docs/04): drop, two rings and logo, animated exactly like design/Gosterge.dc.html.
 *
 * Every change of [visual] plays the matching transitions and effects; a change of
 * [GaugeVisual.pulseTurns] plays the light turn and glint. [speed] scales all durations (the
 * prototype's "Hız"). With [reduceMotion] — by default the system's "remove animations" setting —
 * changes are immediate and the one-off effects do not play. [smallBadge] forces the simple badge symbol
 * (the prototype's `kucuk`, used for the mini widget's drops).
 */
@Composable
fun Damla(
    visual: GaugeVisual,
    size: Dp,
    logo: Logo?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    speed: Double = 1.0,
    reduceMotion: Boolean = !ValueAnimator.areAnimatorsEnabled(),
    smallBadge: Boolean = false,
) {
    val motion = remember(speed, reduceMotion) { GaugeMotion(visual, speed, reduceMotion) }
    var now by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(motion, visual) {
        val start = withFrameNanos { it } / NANOS_PER_MILLI
        motion.update(visual, start)
        now = start
        while (now < motion.settledAt()) {
            now = withFrameNanos { it } / NANOS_PER_MILLI
        }
    }
    Canvas(
        modifier
            .size(size, size * (Geometry.HEIGHT / Geometry.WIDTH).toFloat())
            .semantics { this.contentDescription = contentDescription },
    ) {
        val frame = motion.frame(now)
        val layout = GaugeLayout(this.size.width.toDouble(), forceSmallBadge = smallBadge)
        drawIntoCanvas { canvas -> GaugePainter.paint(frame, layout, logo, ComposeGaugeCanvas(canvas)) }
    }
}

private const val NANOS_PER_MILLI = 1_000_000.0
