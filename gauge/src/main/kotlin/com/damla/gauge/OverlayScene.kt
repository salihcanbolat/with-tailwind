package com.damla.gauge

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The overlay's stages (docs/06): the widget itself, the 2 x 2 flow, and the detail scene. */
public enum class OverlayStage { WIDGET, FLOW, DETAIL }

/** A part that slides in while it fades and sharpens: detail sections (vertical) and labels (horizontal). */
public data class Reveal(
    val alpha: Double,
    val offset: Double,
    val blur: Double,
)

/** One asset's cell: its drop centre, scale and look, in screen design pixels. */
public data class CellFrame(
    val x: Double,
    val y: Double,
    val scale: Double,
    val alpha: Double,
    val blur: Double,
    val shadowAlpha: Double,
    val labelAlpha: Double,
    val smallBadge: Boolean,
)

/** Everything the overlay draws at one moment. Positions are screen design pixels (dp). */
public data class OverlayFrame(
    val homeBlur: Double,
    val homeScale: Double,
    val dimAlpha: Double,
    val detailDimAlpha: Double,
    val boxAlpha: Double,
    val boxScale: Double,
    val boxOriginX: Double,
    val boxOriginY: Double,
    val cells: List<CellFrame>,
    val heroX: Double,
    val heroY: Double,
    val heroScale: Double,
    val heroAlpha: Double,
    /** Detail sections 0 (title), 2 (price), 3 (chart), 4 (range picker), 5 (buttons); index 1 is unused. */
    val sections: List<Reveal>,
    val annotations: List<Reveal>,
    val annotationColors: List<Int>,
    val annotationLineOffsets: List<Double>,
    val annotationDotAlphas: List<Double>,
    val chartLineOffset: Double,
    val chartColor: Int,
    val chartAreaAlpha: Double,
    val chartEndAlpha: Double,
    val chartEndColor: Int,
    val chartPoints: List<Double>,
    val rangePillX: Double,
    val dotWidths: List<Double>,
    val dotAlphas: List<Double>,
    val shareDimAlpha: Double,
    val shareAlpha: Double,
    val shareTranslateY: Double,
    val shareScale: Double,
)

/**
 * The overlay of design/Prototip.dc.html as CSS transitions: every value eases to its new target with the
 * duration, delay and curve the prototype gives it for the change that just happened.
 *
 * Call [show] for stage changes, [select] to switch assets, [pickRange], [share], and [setChart] when new
 * history arrives; then draw [frame]. [chartColor] and [wordColor] supply the colours of the shown states.
 */
public class OverlayScene(
    private val geometry: OverlayGeometry,
    private val assetCount: Int,
    private val flow: Boolean,
    private val mini: Boolean,
    private val bare: Boolean,
    private val speed: Double = 1.0,
    initialChart: List<Double> = List(CHART_POINTS) { CHART_HEIGHT / 2 },
    initialChartColor: Int,
    initialWords: List<Int>,
    initialStage: OverlayStage = OverlayStage.WIDGET,
    initialFrom: OverlayStage = OverlayStage.WIDGET,
    initialSelected: Int = 0,
) {
    public var stage: OverlayStage = initialStage
        private set
    private var from = initialFrom
    public var selected: Int = initialSelected
        private set
    public var range: Int = 1
        private set
    public var sharing: Boolean = false
        private set

    private val inWidget = geometry.widgetDrops.size
    private val homeBlur = NumberTransition(0.0)
    private val homeScale = NumberTransition(1.0)
    private val dim = NumberTransition(0.0)
    private val detailDim = NumberTransition(0.0)
    private val boxAlpha = NumberTransition(1.0)
    private val boxScale = NumberTransition(1.0)
    private val cells = List(assetCount) { CellMotion() }
    private val heroX = NumberTransition(0.0)
    private val heroY = NumberTransition(0.0)
    private val heroScale = NumberTransition(0.5)
    private val heroAlpha = NumberTransition(0.0)
    private val sections = List(SECTIONS) { RevealMotion(hidden = SECTION_OFFSET, blur = SECTION_BLUR) }
    private val annotations = List(3) { RevealMotion(hidden = ANNOTATION_OFFSET, blur = ANNOTATION_BLUR) }
    private val annotationColors = initialWords.map(::ColorTransition)
    private val annotationLines = List(3) { NumberTransition(ANNOTATION_LINE) }
    private val annotationDots = List(3) { NumberTransition(0.0) }
    private val chartLine = NumberTransition(CHART_LINE)
    private val chartColorTween = ColorTransition(initialChartColor)
    private val chartArea = NumberTransition(0.0)
    private val chartEnd = NumberTransition(0.0)
    private val chartEndColor = ColorTransition(initialChartColor)
    private val chart = initialChart.map(::NumberTransition)
    private val rangePill = NumberTransition(range * RANGE_STEP)
    private val dotWidths = List(assetCount) { NumberTransition(if (it == selected) DOT_ON else DOT_OFF) }
    private val dotAlphas = List(assetCount) { NumberTransition(if (it == selected) 1.0 else DOT_DIM) }
    private val shareDim = NumberTransition(0.0)
    private val shareAlpha = NumberTransition(0.0)
    private val shareY = NumberTransition(SHARE_OFFSET)
    private val shareScale = NumberTransition(SHARE_SCALE)

    init {
        apply(0.0, instant = true)
    }

    /** Moves to [next] at [atMs], optionally opening the detail of asset [selected]. */
    public fun show(
        next: OverlayStage,
        atMs: Double,
        selected: Int = this.selected,
    ) {
        from = stage
        stage = next
        this.selected = selected
        apply(atMs)
    }

    /** Switches the detail scene to another asset (swipe or dots). */
    public fun select(
        index: Int,
        atMs: Double,
    ) {
        selected = index
        apply(atMs)
    }

    public fun pickRange(
        index: Int,
        atMs: Double,
    ) {
        range = index
        apply(atMs)
    }

    public fun share(
        open: Boolean,
        atMs: Double,
    ) {
        sharing = open
        apply(atMs)
    }

    /** New chart line ([CHART_POINTS] y values, 0 at the top of the 54-high chart), in the given colour. */
    public fun setChart(
        points: List<Double>,
        color: Int,
        words: List<Int>,
        atMs: Double,
    ) {
        require(points.size == CHART_POINTS) { "The chart always has $CHART_POINTS points" }
        val morph = ms(CHART_MORPH)
        points.forEachIndexed { i, y -> chart[i].to(y, atMs, morph, curve = Curves.easeStandard) }
        chartColorTween.to(color, atMs, ms(COLOR_CHANGE))
        chartEndColor.to(color, atMs, COLOR_CHANGE.toDouble())
        words.forEachIndexed { i, word -> annotationColors[i].to(word, atMs, WORD_COLOR.toDouble()) }
    }

    public fun frame(atMs: Double): OverlayFrame {
        val box = boxOrigin()
        return OverlayFrame(
            homeBlur = homeBlur.value(atMs),
            homeScale = homeScale.value(atMs),
            dimAlpha = dim.value(atMs),
            detailDimAlpha = detailDim.value(atMs),
            boxAlpha = boxAlpha.value(atMs),
            boxScale = boxScale.value(atMs),
            boxOriginX = box.first,
            boxOriginY = box.second,
            cells = cells.mapIndexed { i, cell -> cell.frame(i, atMs) },
            heroX = heroX.value(atMs),
            heroY = heroY.value(atMs),
            heroScale = heroScale.value(atMs),
            heroAlpha = heroAlpha.value(atMs),
            sections = sections.map { it.frame(atMs) },
            annotations = annotations.map { it.frame(atMs) },
            annotationColors = annotationColors.map { it.value(atMs) },
            annotationLineOffsets = annotationLines.map { it.value(atMs) },
            annotationDotAlphas = annotationDots.map { it.value(atMs) },
            chartLineOffset = chartLine.value(atMs),
            chartColor = chartColorTween.value(atMs),
            chartAreaAlpha = chartArea.value(atMs),
            chartEndAlpha = chartEnd.value(atMs),
            chartEndColor = chartEndColor.value(atMs),
            chartPoints = chart.map { it.value(atMs) },
            rangePillX = rangePill.value(atMs),
            dotWidths = dotWidths.map { it.value(atMs) },
            dotAlphas = dotAlphas.map { it.value(atMs) },
            shareDimAlpha = shareDim.value(atMs),
            shareAlpha = shareAlpha.value(atMs),
            shareTranslateY = shareY.value(atMs),
            shareScale = shareScale.value(atMs),
        )
    }

    /** The detail gauge's resting place: its body centre lines up with the annotation lines (design: 44 / 100). */
    public val heroTarget: Pair<Double, Double>
        get() {
            val k = OverlayGeometry.DETAIL_SCALE
            val x =
                if (geometry.right) {
                    geometry.detailLeft + OverlayGeometry.DETAIL_WIDTH - (OverlayGeometry.DETAIL_WIDTH - HERO_LEFT) * k
                } else {
                    geometry.detailLeft + HERO_LEFT * k
                }
            return x to geometry.detailTop + HERO_TOP * k
        }

    private fun apply(
        atMs: Double,
        instant: Boolean = false,
    ) {
        fun ms(base: Int) = if (instant) 0.0 else this.ms(base)
        val open = stage != OverlayStage.WIDGET
        val detail = stage == OverlayStage.DETAIL

        homeBlur.to(if (open) HOME_BLUR else 0.0, atMs, ms(520))
        homeScale.to(if (open) HOME_SCALE else 1.0, atMs, ms(600), curve = Curves.easeStandard)
        dim.to(if (open) 1.0 else 0.0, atMs, ms(420))
        detailDim.to(if (detail) 1.0 else 0.0, atMs, ms(420))
        boxAlpha.to(if (detail) 0.0 else 1.0, atMs, ms(260))
        boxScale.to(if (detail) DETAIL_BOX_SCALE else 1.0, atMs, ms(600), curve = Curves.easeStandard)

        val chosen = position(selected)
        for ((i, cell) in cells.withIndex()) cell.apply(i, chosen, atMs, ::ms)

        val k = OverlayGeometry.DETAIL_SCALE
        if (detail) {
            val (hx, hy) = heroTarget
            heroX.to(round1(hx - HERO_HALF_WIDTH * k), atMs, ms(640), curve = Curves.easeStandard)
            heroY.to(round1(hy - HERO_HALF_HEIGHT * k), atMs, ms(640), curve = Curves.easeStandard)
            heroScale.to(k, atMs, ms(640), curve = Curves.easeStandard)
            heroAlpha.to(1.0, atMs, ms(1))
        } else {
            val small = OverlayGeometry.CELL_GAUGE / OverlayGeometry.HERO_GAUGE
            heroX.to(round1(chosen.first - HERO_HALF_WIDTH * small), atMs, ms(440), curve = Curves.easeStandard)
            heroY.to(round1(chosen.second - HERO_HALF_HEIGHT * small), atMs, ms(440), curve = Curves.easeStandard)
            heroScale.to(small, atMs, ms(440), curve = Curves.easeStandard)
            heroAlpha.to(0.0, atMs, ms(90), delay = ms(380))
        }

        for ((n, section) in sections.withIndex()) {
            val delay = ms(190 + n * 40)
            if (detail) {
                section.show(atMs, ms(340), ms(540), ms(380), delay, Curves.easeStandard)
            } else {
                section.hide(atMs, ms(110), ms(200), ms(150), Curves.ease)
            }
        }
        for ((j, annotation) in annotations.withIndex()) {
            val delay = ms(460 + j * 70)
            if (detail) {
                annotation.show(atMs, ms(340), ms(520), ms(360), delay, Curves.easeStandard)
            } else {
                annotation.hide(atMs, ms(110), ms(520), ms(360), Curves.easeStandard)
            }
            annotationLines[j].to(
                if (detail) 0.0 else ANNOTATION_LINE,
                atMs,
                ms(if (detail) 520 else 140),
                if (detail) ms(380 + j * 70) else 0.0,
                Curves.easeStandard,
            )
            annotationDots[j].to(if (detail) 1.0 else 0.0, atMs, ms(if (detail) 260 else 100), if (detail) ms(360 + j * 70) else 0.0)
        }
        chartLine.to(if (detail) 0.0 else CHART_LINE, atMs, ms(1000), if (detail) ms(380) else 0.0, Curves.easeStandard)
        chartArea.to(if (detail) CHART_AREA else 0.0, atMs, ms(600), if (detail) ms(760) else 0.0)
        chartEnd.to(if (detail) 1.0 else 0.0, atMs, ms(300), if (detail) ms(1200) else 0.0)
        rangePill.to(range * RANGE_STEP, atMs, ms(420), curve = Curves.springSoft)
        for (i in 0 until assetCount) {
            val on = i == selected
            // These two durations are not scaled by the speed in the prototype.
            dotWidths[i].to(if (on) DOT_ON else DOT_OFF, atMs, if (instant) 0.0 else DOT_CHANGE.toDouble(), curve = Curves.easeStandard)
            dotAlphas[i].to(if (on) 1.0 else DOT_DIM, atMs, if (instant) 0.0 else DOT_CHANGE.toDouble())
        }
        val shared = sharing && detail
        shareDim.to(if (shared) 1.0 else 0.0, atMs, ms(320))
        shareAlpha.to(if (shared) 1.0 else 0.0, atMs, ms(320))
        shareY.to(if (shared) 0.0 else SHARE_OFFSET, atMs, ms(560), curve = Curves.easeStandard)
        shareScale.to(if (shared) 1.0 else SHARE_SCALE, atMs, ms(560), curve = Curves.easeStandard)
    }

    /** Where cell [i] sits in the open stages: the flow grid or the folder grid. */
    private fun position(i: Int): Pair<Double, Double> = if (flow) geometry.flowCell(i) else geometry.folderCell(i)

    /** The box (widget, or folder) scales from the chosen cell's centre, rounded like the prototype. */
    private fun boxOrigin(): Pair<Double, Double> {
        val (x, y) = position(selected)
        val left = if (stage != OverlayStage.WIDGET && !flow) geometry.folderLeft else geometry.widgetLeft
        return left + (x - left).roundToInt() to geometry.widgetTop + (y - geometry.widgetTop).roundToInt()
    }

    private fun ms(base: Int): Double = (base * speed).roundToInt().toDouble()

    private inner class CellMotion {
        private val x = NumberTransition(0.0)
        private val y = NumberTransition(0.0)
        private val scale = NumberTransition(1.0)
        private val alpha = NumberTransition(1.0)
        private val blur = NumberTransition(0.0)
        private val shadow = NumberTransition(0.0)
        private val label = NumberTransition(1.0)
        private var small = false

        fun apply(
            i: Int,
            chosen: Pair<Double, Double>,
            atMs: Double,
            ms: (Int) -> Double,
        ) {
            val (cx, cy) = geometry.folderCell(i)
            val (px, py) = position(i)
            var tx = 0.0
            var ty = 0.0
            var s = 1.0
            var opacity = 1.0
            var blurTo = 0.0
            var labelTo = 1.0
            // Transition specs: duration, delay, curve per property; null means the change is immediate.
            var move: Spec? = null
            var fade: Spec? = null
            var filter: Spec? = null
            var labelFade: Spec?
            when (stage) {
                OverlayStage.WIDGET -> {
                    labelTo = 0.0
                    if (i < inWidget) {
                        val (wx, wy) = geometry.widgetDrops[i]
                        tx = wx - cx
                        ty = wy - cy
                        s = geometry.dropSize / OverlayGeometry.CELL_GAUGE
                        move = Spec(ms(540), 0.0, Curves.easeStandard)
                        fade = Spec(ms(200), if (from == OverlayStage.DETAIL) ms(360) else 0.0, Curves.ease)
                    } else {
                        val (mx, my) = geometry.widgetMiddle
                        tx = (mx - cx) * 0.45
                        ty = (my - cy) * 0.45
                        s = 0.7
                        opacity = 0.0
                        move = Spec(ms(420), 0.0, Curves.easeExit)
                        fade = Spec(ms(160), 0.0, Curves.ease)
                    }
                    labelFade = Spec(ms(90), 0.0, Curves.ease)
                }

                OverlayStage.FLOW -> {
                    tx = px - cx
                    ty = py - cy
                    if (from == OverlayStage.DETAIL) {
                        val chosenOne = i == selected
                        move = Spec(ms(520), 0.0, Curves.easeStandard)
                        fade = Spec(ms(if (chosenOne) 120 else 300), if (chosenOne) ms(360) else 0.0, Curves.ease)
                        filter = Spec(ms(300), 0.0, Curves.ease)
                        labelFade = Spec(ms(240), 0.0, Curves.ease)
                    } else {
                        val delay = if (i < 2) 0.0 else ms(60 + i * 40)
                        move = Spec(ms(if (i < 2) 600 else 560), delay, if (i < 2) Curves.springSoft else Curves.springOpen)
                        fade = Spec(ms(240), delay, Curves.ease)
                        filter = Spec(ms(300), 0.0, Curves.ease)
                        labelFade = Spec(ms(260), ms(200 + i * 40), Curves.ease)
                        if (flow) {
                            val stagger = ms(i * 45)
                            move = Spec(ms(560), stagger, Curves.springSoft)
                            fade = Spec(ms(200), stagger, Curves.ease)
                        }
                    }
                }

                OverlayStage.DETAIL -> {
                    if (i == selected) {
                        opacity = 0.0
                        fade = Spec(ms(70), 0.0, Curves.ease)
                    } else {
                        val vx = px - chosen.first
                        val vy = py - chosen.second
                        // Cells never share a position, so the length is never zero.
                        val length = hypot(vx, vy)
                        tx = px - cx + vx / length * PUSH_AWAY
                        ty = py - cy + vy / length * PUSH_AWAY
                        s = 0.85
                        opacity = 0.0
                        blurTo = 2.0
                        move = Spec(ms(560), 0.0, Curves.easeStandard)
                        fade = Spec(ms(280), 0.0, Curves.ease)
                        filter = Spec(ms(280), 0.0, Curves.ease)
                    }
                    labelFade = Spec(ms(180), 0.0, Curves.ease)
                }
            }
            val shadowTo = if ((bare && stage == OverlayStage.WIDGET) || (flow && stage != OverlayStage.WIDGET)) SHADOW else 0.0
            x.to(cx + round1(tx), atMs, move)
            y.to(cy + round1(ty), atMs, move)
            scale.to(round3(s), atMs, move)
            alpha.to(opacity, atMs, fade)
            blur.to(blurTo, atMs, filter)
            shadow.to(shadowTo, atMs, filter)
            label.to(labelTo, atMs, labelFade)
            small = stage == OverlayStage.WIDGET && mini
        }

        fun frame(
            i: Int,
            atMs: Double,
        ) = CellFrame(
            x.value(atMs),
            y.value(atMs),
            scale.value(atMs),
            alpha.value(atMs),
            blur.value(atMs),
            shadow.value(atMs),
            label.value(atMs),
            small,
        )
    }

    private class RevealMotion(
        private val hidden: Double,
        private val blur: Double,
    ) {
        private val alpha = NumberTransition(0.0)
        private val offset = NumberTransition(hidden)
        private val sharpness = NumberTransition(blur)

        fun show(
            atMs: Double,
            fade: Double,
            move: Double,
            focus: Double,
            delay: Double,
            curve: CubicBezier,
        ) {
            alpha.to(1.0, atMs, fade, delay)
            offset.to(0.0, atMs, move, delay, curve)
            sharpness.to(0.0, atMs, focus, delay)
        }

        fun hide(
            atMs: Double,
            fade: Double,
            move: Double,
            focus: Double,
            curve: CubicBezier,
        ) {
            alpha.to(0.0, atMs, fade)
            offset.to(hidden, atMs, move, curve = curve)
            sharpness.to(blur, atMs, focus)
        }

        fun frame(atMs: Double) = Reveal(alpha.value(atMs), offset.value(atMs), sharpness.value(atMs))
    }

    private class Spec(
        val duration: Double,
        val delay: Double,
        val curve: CubicBezier,
    )

    private fun NumberTransition.to(
        value: Double,
        atMs: Double,
        spec: Spec?,
    ) = if (spec == null) to(value, atMs, 0.0) else to(value, atMs, spec.duration, spec.delay, spec.curve)

    public companion object {
        public const val CHART_POINTS: Int = 24
        public const val CHART_WIDTH: Double = 260.0
        public const val CHART_HEIGHT: Double = 54.0
        public const val RANGE_STEP: Double = 64.5
        public const val DOT_ON: Double = 18.0
        public const val DOT_OFF: Double = 6.0
        internal const val DOT_DIM = 0.45
        internal const val DOT_CHANGE = 300
        internal const val COLOR_CHANGE = 400
        internal const val WORD_COLOR = 300
        internal const val CHART_MORPH = 560
        internal const val SECTIONS = 6
        internal const val SECTION_OFFSET = 8.0
        internal const val SECTION_BLUR = 4.0
        internal const val ANNOTATION_OFFSET = -8.0
        internal const val ANNOTATION_BLUR = 3.0
        internal const val ANNOTATION_LINE = 140.0
        internal const val CHART_LINE = 600.0
        internal const val CHART_AREA = 0.2
        internal const val HOME_BLUR = 6.0
        internal const val HOME_SCALE = 0.985
        internal const val DETAIL_BOX_SCALE = 0.985
        internal const val SHADOW = 0.35
        internal const val PUSH_AWAY = 14.0
        internal const val SHARE_OFFSET = 24.0
        internal const val SHARE_SCALE = 0.92

        /** Where the detail gauge's body centre sits in the 260-wide scene, and half its 80 x 97 box. */
        internal const val HERO_LEFT = 44.0
        internal const val HERO_TOP = 100.0
        internal const val HERO_HALF_WIDTH = 40.0
        internal const val HERO_HALF_HEIGHT = 48.5

        /** JavaScript's `toFixed`: the exact binary value decides, and ties round away from zero. */
        private fun toFixed(
            value: Double,
            digits: Int,
        ) = BigDecimal(value).setScale(digits, RoundingMode.HALF_UP).toDouble()

        private fun round1(value: Double) = toFixed(value, 1)

        private fun round3(value: Double) = toFixed(value, 3)
    }
}
