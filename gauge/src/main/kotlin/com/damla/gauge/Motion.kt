package com.damla.gauge

import kotlin.math.roundToInt

/** Durations, delays and curves of every layer (docs/04, design/Gosterge.dc.html), in milliseconds. */
public object MotionSpec {
    public const val SHAPE: Int = 700
    public const val CIRCLE_SHAPE: Int = 600
    public const val FADE: Int = 260
    public const val COLOR: Int = 450
    public const val LOWER_RING_COLOR_DELAY: Int = 60
    public const val SQUASH: Int = 720
    public const val SQUASH_DELAY: Int = 140
    public const val HALO: Int = 1300
    public const val REDRAW: Int = 720
    public const val UPPER_REDRAW_DELAY: Int = 200
    public const val LOWER_REDRAW_DELAY: Int = 320
    public const val PULSE: Int = 1100

    /** Squash keyframes: offset, scale x, scale y. */
    internal val squashOffsets = doubleArrayOf(0.0, 0.22, 0.5, 0.76, 1.0)
    internal val squashX = doubleArrayOf(1.0, 1.07, 0.96, 1.015, 1.0)
    internal val squashY = doubleArrayOf(1.0, 0.9, 1.05, 0.99, 1.0)

    /** Halo keyframes. Opacity and scale have their own keyframes, so their segments differ. */
    internal val haloAlphaOffsets = doubleArrayOf(0.0, 0.25, 1.0)
    internal val haloAlpha = doubleArrayOf(0.0, 0.5, 0.0)
    internal val haloScaleOffsets = doubleArrayOf(0.0, 1.0)
    internal val haloScale = doubleArrayOf(0.6, 1.7)

    /** Glint keyframes: opacity and a shift by 10% / 12% of the highlight's own size. */
    internal val glintOffsets = doubleArrayOf(0.0, 0.35, 1.0)
    internal val glintAlpha = doubleArrayOf(Geometry.HIGHLIGHT_ALPHA, 0.7, Geometry.HIGHLIGHT_ALPHA)
    internal val glintShift = doubleArrayOf(0.0, 1.0, 0.0)
    internal const val GLINT_SHIFT_X: Double = 0.10 * Geometry.HIGHLIGHT_WIDTH
    internal const val GLINT_SHIFT_Y: Double = 0.12 * Geometry.HIGHLIGHT_HEIGHT
}

/** Everything needed to draw one frame, in design units (see [Geometry]) and 0xAARRGGBB colours. */
public data class GaugeFrame(
    val bodyColor: Int,
    val dropTranslateY: Double,
    val dropRotation: Double,
    val dropScale: Double,
    val dropAlpha: Double,
    val circleScale: Double,
    val circleAlpha: Double,
    val squashX: Double,
    val squashY: Double,
    val haloX: Double,
    val haloY: Double,
    val haloColor: Int,
    val haloAlpha: Double,
    val haloScale: Double,
    val upperRingColor: Int,
    val upperRingDashOffset: Double,
    val lowerRingColor: Int,
    val lowerRingDashOffset: Double,
    val logoX: Double,
    val logoY: Double,
    val logoTextColor: Int,
    val glintAlpha: Double,
    val glintX: Double,
    val glintY: Double,
    val lightRotation: Double,
)

/**
 * The motion of one gauge, as the prototype plays it in a browser: every property eases from its current
 * value to the new one when the visual changes (CSS transitions), and a shape change, a ring change or a
 * new price restarts the one-off effects (CSS keyframe animations, eased per keyframe segment).
 *
 * Times are milliseconds on any monotonic clock. [speed] multiplies every duration and delay, rounded to
 * whole milliseconds like the prototype's `t(ms)`. With [reduceMotion] changes are immediate and the
 * squash, halo, ring redraw and glint do not play.
 */
public class GaugeMotion(
    initial: GaugeVisual,
    private val speed: Double = 1.0,
    private val reduceMotion: Boolean = false,
) {
    public var visual: GaugeVisual = initial
        private set

    private val pose = Geometry.dropPose(initial.shape)
    private val dropTranslateY = Tween(pose.translateY)
    private val dropRotation = Tween(pose.rotation)
    private val dropScale = Tween(pose.scale)
    private val dropAlpha = Tween(dropAlphaOf(initial.shape))
    private val circleScale = Tween(circleScaleOf(initial.shape))
    private val circleAlpha = Tween(1 - dropAlphaOf(initial.shape))
    private val logoX = Tween(Geometry.badgeCentre(initial.shape).first)
    private val logoY = Tween(Geometry.badgeCentre(initial.shape).second)
    private val bodyColor = ColorTween(bodyColorOf(initial))
    private val upperColor = ColorTween(ringColorOf(initial.upperRing, initial.closed))
    private val lowerColor = ColorTween(ringColorOf(initial.lowerRing, initial.closed))
    private val logoColor = ColorTween(logoColorOf(initial))
    private val lightRotation = Tween(360.0 * initial.pulseTurns)

    private var shapeChangedAt: Double? = null
    private var upperRedrawAt: Double? = null
    private var lowerRedrawAt: Double? = null
    private var pulsedAt: Double? = null

    /** Applies a new visual at [atMs]. Running transitions continue from where they are. */
    public fun update(
        next: GaugeVisual,
        atMs: Double,
    ) {
        val previous = visual
        if (next == previous) return
        visual = next
        val shapeChanged = next.shape != previous.shape

        val pose = Geometry.dropPose(next.shape)
        val shape = ms(MotionSpec.SHAPE)
        dropTranslateY.to(pose.translateY, atMs, shape, curve = Curves.springShape)
        dropRotation.to(pose.rotation, atMs, shape, curve = Curves.springShape)
        dropScale.to(pose.scale, atMs, shape, curve = Curves.springShape)
        dropAlpha.to(dropAlphaOf(next.shape), atMs, ms(MotionSpec.FADE), curve = Curves.ease)
        circleScale.to(circleScaleOf(next.shape), atMs, ms(MotionSpec.CIRCLE_SHAPE), curve = Curves.springShape)
        circleAlpha.to(1 - dropAlphaOf(next.shape), atMs, ms(MotionSpec.FADE), curve = Curves.ease)
        val (badgeX, badgeY) = Geometry.badgeCentre(next.shape)
        logoX.to(badgeX, atMs, shape, curve = Curves.springShape)
        logoY.to(badgeY, atMs, shape, curve = Curves.springShape)

        val color = ms(MotionSpec.COLOR)
        bodyColor.to(bodyColorOf(next), atMs, color)
        upperColor.to(ringColorOf(next.upperRing, next.closed), atMs, color)
        lowerColor.to(ringColorOf(next.lowerRing, next.closed), atMs, color, delay = ms(MotionSpec.LOWER_RING_COLOR_DELAY))
        logoColor.to(logoColorOf(next), atMs, color)

        val pulsed = next.pulseTurns != previous.pulseTurns
        lightRotation.to(360.0 * next.pulseTurns, atMs, ms(MotionSpec.PULSE), curve = Curves.pulse)

        if (reduceMotion) return
        if (shapeChanged) shapeChangedAt = atMs
        // docs/04: a ring redraws when its colour or the drop's state changes. (The prototype skips the
        // redraw when both change in the same update; the document wins.)
        if (shapeChanged || next.upperRing != previous.upperRing) upperRedrawAt = atMs
        if (shapeChanged || next.lowerRing != previous.lowerRing) lowerRedrawAt = atMs
        if (pulsed) pulsedAt = atMs
    }

    public fun frame(atMs: Double): GaugeFrame {
        val sinceShape = shapeChangedAt?.let { atMs - it }
        val squash = sinceShape?.let { progress(it, MotionSpec.SQUASH_DELAY, MotionSpec.SQUASH).coerceIn(0.0, 1.0) }
        val halo = sinceShape?.let { progress(it, 0, MotionSpec.HALO).coerceIn(0.0, 1.0) }
        val glint =
            pulsedAt
                ?.let { progress(atMs - it, 0, MotionSpec.PULSE) }
                ?.takeIf { it >= 0.0 && it < 1.0 }
        val (haloX, haloY) = Geometry.badgeCentre(visual.shape)
        return GaugeFrame(
            bodyColor = bodyColor.value(atMs),
            dropTranslateY = dropTranslateY.value(atMs),
            dropRotation = dropRotation.value(atMs),
            dropScale = dropScale.value(atMs),
            dropAlpha = dropAlpha.value(atMs),
            circleScale = circleScale.value(atMs),
            circleAlpha = circleAlpha.value(atMs),
            squashX = squash?.let { keyframes(MotionSpec.squashOffsets, MotionSpec.squashX, it, Curves.squash) } ?: 1.0,
            squashY = squash?.let { keyframes(MotionSpec.squashOffsets, MotionSpec.squashY, it, Curves.squash) } ?: 1.0,
            haloX = haloX,
            haloY = haloY,
            haloColor = bodyColorOf(visual),
            haloAlpha = halo?.let { keyframes(MotionSpec.haloAlphaOffsets, MotionSpec.haloAlpha, it, Curves.halo) } ?: 0.0,
            haloScale = halo?.let { keyframes(MotionSpec.haloScaleOffsets, MotionSpec.haloScale, it, Curves.halo) } ?: 1.0,
            upperRingColor = upperColor.value(atMs),
            upperRingDashOffset = dashOffset(upperRedrawAt, atMs, MotionSpec.UPPER_REDRAW_DELAY),
            lowerRingColor = lowerColor.value(atMs),
            lowerRingDashOffset = dashOffset(lowerRedrawAt, atMs, MotionSpec.LOWER_REDRAW_DELAY),
            logoX = logoX.value(atMs),
            logoY = logoY.value(atMs),
            logoTextColor = logoColor.value(atMs),
            glintAlpha =
                glint?.let { keyframes(MotionSpec.glintOffsets, MotionSpec.glintAlpha, it, Curves.pulse) } ?: Geometry.HIGHLIGHT_ALPHA,
            glintX =
                glint?.let {
                    MotionSpec.GLINT_SHIFT_X *
                        keyframes(
                            MotionSpec.glintOffsets,
                            MotionSpec.glintShift,
                            it,
                            Curves.pulse,
                        )
                } ?: 0.0,
            glintY =
                glint?.let {
                    MotionSpec.GLINT_SHIFT_Y *
                        keyframes(
                            MotionSpec.glintOffsets,
                            MotionSpec.glintShift,
                            it,
                            Curves.pulse,
                        )
                } ?: 0.0,
            lightRotation = lightRotation.value(atMs),
        )
    }

    /** The time from which frames no longer change, so a renderer can stop asking for new ones. */
    public fun settledAt(): Double {
        val tweens =
            listOf(dropTranslateY, dropRotation, dropScale, dropAlpha, circleScale, circleAlpha, logoX, logoY, lightRotation)
                .map { it.end } + listOf(bodyColor, upperColor, lowerColor, logoColor).map { it.end }
        val effects =
            listOfNotNull(
                shapeChangedAt?.plus(maxOf(ms(MotionSpec.SQUASH_DELAY) + ms(MotionSpec.SQUASH), ms(MotionSpec.HALO))),
                upperRedrawAt?.plus(ms(MotionSpec.UPPER_REDRAW_DELAY) + ms(MotionSpec.REDRAW)),
                lowerRedrawAt?.plus(ms(MotionSpec.LOWER_REDRAW_DELAY) + ms(MotionSpec.REDRAW)),
                pulsedAt?.plus(ms(MotionSpec.PULSE)),
            )
        return (effects + tweens).max()
    }

    private fun dashOffset(
        startedAt: Double?,
        atMs: Double,
        delay: Int,
    ): Double {
        startedAt ?: return 0.0
        val p = progress(atMs - startedAt, delay, MotionSpec.REDRAW).coerceIn(0.0, 1.0)
        return 100 * (1 - Curves.easeStandard.transform(p))
    }

    /** Scaled like the prototype's `t(ms) = Math.round(ms * hiz)`; zero when motion is reduced. */
    private fun ms(base: Int): Double = if (reduceMotion) 0.0 else (base * speed).roundToInt().toDouble()

    /** Progress of a one-off effect [elapsed] ms after its trigger; may be outside 0..1. */
    private fun progress(
        elapsed: Double,
        delay: Int,
        duration: Int,
    ): Double = (elapsed - ms(delay)) / ms(duration)

    private fun dropAlphaOf(shape: Shape) = if (shape == Shape.FLAT) 0.0 else 1.0

    private fun circleScaleOf(shape: Shape) = if (shape == Shape.FLAT) 1.0 else 0.5

    private fun bodyColorOf(visual: GaugeVisual) = if (visual.closed) GaugeColors.CLOSED else GaugeColors.of(visual.shape)

    private fun ringColorOf(
        ring: Shape,
        closed: Boolean,
    ) = if (closed) GaugeColors.CLOSED else GaugeColors.of(ring)

    private fun logoColorOf(visual: GaugeVisual) =
        when {
            visual.closed -> GaugeColors.WHITE
            visual.shape == Shape.FLAT -> GaugeColors.LOGO_ON_FLAT
            else -> GaugeColors.WHITE
        }
}

/**
 * Value of a keyframe track at overall [progress] (0..1). Like CSS, the timing function applies to each
 * segment between two keyframes, not to the whole animation.
 */
internal fun keyframes(
    offsets: DoubleArray,
    values: DoubleArray,
    progress: Double,
    curve: CubicBezier,
): Double {
    var segment = 0
    while (segment < offsets.size - 2 && progress >= offsets[segment + 1]) segment++
    val start = offsets[segment]
    val end = offsets[segment + 1]
    val local = curve.transform((progress - start) / (end - start))
    return values[segment] + (values[segment + 1] - values[segment]) * local
}

/** A CSS transition of one number: eases from where it is towards a new target. */
private class Tween(
    initial: Double,
) {
    private var from = initial
    private var target = initial
    private var start = 0.0
    private var delay = 0.0
    private var duration = 0.0
    private var curve = Curves.ease

    val end: Double get() = start + delay + duration

    fun value(atMs: Double): Double {
        val elapsed = atMs - start - delay
        val p = if (duration <= 0.0) (if (elapsed >= 0.0) 1.0 else 0.0) else (elapsed / duration).coerceIn(0.0, 1.0)
        return from + (target - from) * curve.transform(p)
    }

    fun to(
        value: Double,
        atMs: Double,
        duration: Double,
        delay: Double = 0.0,
        curve: CubicBezier,
    ) {
        if (value == target) return
        from = value(atMs)
        target = value
        start = atMs
        this.delay = delay
        this.duration = duration
        this.curve = curve
    }
}

/** A CSS colour transition with `ease`, interpolated per channel in sRGB. */
private class ColorTween(
    initial: Int,
) {
    private var from = initial
    private var target = initial
    private var start = 0.0
    private var delay = 0.0
    private var duration = 0.0

    val end: Double get() = start + delay + duration

    fun value(atMs: Double): Int {
        val elapsed = atMs - start - delay
        val p = if (duration <= 0.0) (if (elapsed >= 0.0) 1.0 else 0.0) else (elapsed / duration).coerceIn(0.0, 1.0)
        return GaugeColors.lerp(from, target, Curves.ease.transform(p))
    }

    fun to(
        value: Int,
        atMs: Double,
        duration: Double,
        delay: Double = 0.0,
    ) {
        if (value == target) return
        from = value(atMs)
        target = value
        start = atMs
        this.delay = delay
        this.duration = duration
    }
}
