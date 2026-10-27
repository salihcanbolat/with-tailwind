package com.damla.gauge

/**
 * Where things sit around the widget, in design pixels (dp on Android). design/Prototip.dc.html lays out a
 * 390-wide phone with the widget's top at 122; these values generalise its formulas to any widget position.
 *
 * @property right the widget is on the right half of the screen, so scenes lean right ("taraf: sağ").
 * @property widgetDrops centres of the drops shown in the widget, in order.
 * @property dropSize width of those drops.
 * @property edgeColumnX centre of the widget's outermost cell on its side; the flow is anchored there.
 * @property dropY centre line of the widget's drops; the flow's first row stays on it.
 */
public data class OverlayGeometry(
    val right: Boolean,
    val screenWidth: Double,
    val screenHeight: Double,
    val widgetLeft: Double,
    val widgetTop: Double,
    val widgetWidth: Double,
    val widgetHeight: Double,
    val widgetDrops: List<Pair<Double, Double>>,
    val dropSize: Double,
    val edgeColumnX: Double,
    val dropY: Double,
) {
    /** Right and left limits of scenes: 10 from the screen edges. */
    internal val rightEdge: Double get() = screenWidth - MARGIN
    internal val leftEdge: Double get() = MARGIN

    internal val widgetMiddle: Pair<Double, Double> get() = widgetLeft + widgetWidth / 2 to widgetTop + widgetHeight / 2

    /** Bottom limit of scenes. */
    private val bottomEdge: Double get() = screenHeight - MARGIN

    /**
     * The glass folder (Klasör), 176 x 216: its edge on the widget's side lines up with the widget, and its
     * top with the widget's top unless that would leave the screen.
     */
    public val folderLeft: Double
        get() =
            if (right) {
                (minOf(widgetLeft + widgetWidth, rightEdge) - FOLDER_WIDTH).coerceAtLeast(leftEdge)
            } else {
                widgetLeft.coerceIn(leftEdge, rightEdge - FOLDER_WIDTH)
            }
    public val folderTop: Double get() = minOf(widgetTop, bottomEdge - FOLDER_HEIGHT)

    /** Drop centre of cell [i] in the 2 x 2 folder grid. */
    internal fun folderCell(i: Int): Pair<Double, Double> = folderLeft + 10 + 78 * (i % 2) + 39 to folderTop + 66 + 85 * (i / 2)

    /**
     * Drop centre of cell [i] in the flow: a tight 2 x 2 grid leaning on the widget's edge (70 x 80). Its
     * first row stays on the widget's row; the second goes below, or above when there is no room below.
     */
    internal fun flowCell(i: Int): Pair<Double, Double> {
        val x = if (right) edgeColumnX - FLOW_STEP_X * (1 - i % 2) else edgeColumnX + FLOW_STEP_X * (i % 2)
        val down = dropY + FLOW_STEP_Y + FLOW_CELL_BELOW <= bottomEdge
        return x to dropY + FLOW_STEP_Y * (i / 2) * (if (down) 1 else -1)
    }

    /** Left and top of the detail scene (260 x 380, scaled by 0.86 from its top corner on the widget's side). */
    public val detailLeft: Double get() = if (right) rightEdge - DETAIL_WIDTH else leftEdge
    public val detailTop: Double get() = minOf(widgetTop + 10, bottomEdge - DETAIL_HEIGHT * DETAIL_SCALE)

    public companion object {
        internal const val MARGIN = 10.0
        public const val FOLDER_WIDTH: Double = 176.0
        public const val FOLDER_HEIGHT: Double = 216.0
        internal const val FLOW_STEP_X = 70.0
        internal const val FLOW_STEP_Y = 80.0

        /** How far a flow cell reaches below its drop centre: the rest of the drop and two label lines. */
        internal const val FLOW_CELL_BELOW = 56.0
        public const val DETAIL_WIDTH: Double = 260.0
        public const val DETAIL_HEIGHT: Double = 380.0
        public const val DETAIL_SCALE: Double = 0.86

        /** Cells draw their gauge at this width and scale it (design: `size="40"`). */
        public const val CELL_GAUGE: Double = 40.0

        /** The detail scene's gauge (design: `size="80"`). */
        public const val HERO_GAUGE: Double = 80.0

        /**
         * The geometry around a widget whose bounds on a [screenWidth] x [screenHeight] screen are [left], [top],
         * [width], [height] (Android's `Intent.getSourceBounds`, in dp). The widget's side is the half of the
         * screen its centre is on.
         */
        public fun forWidget(
            screenWidth: Double,
            screenHeight: Double,
            left: Double,
            top: Double,
            width: Double,
            height: Double,
            arrangement: WidgetArrangement,
        ): OverlayGeometry {
            val right = left + width / 2 > screenWidth / 2
            return OverlayGeometry(
                right = right,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                widgetLeft = left,
                widgetTop = top,
                widgetWidth = width,
                widgetHeight = height,
                widgetDrops = arrangement.dropCentres(width, height).map { (x, y) -> left + x to top + y },
                dropSize = arrangement.dropSize,
                edgeColumnX = left + arrangement.edgeColumnX(width, right),
                dropY = top + (height - arrangement.blockHeight) / 2 + arrangement.dropCentreY,
            )
        }

        /**
         * The geometry of design/Prototip.dc.html for a widget [size] and [style], on a 390-wide screen with
         * the 4-column home grid (columns 97.5 wide, widget top at 122).
         */
        public fun prototype(
            size: PrototypeWidget,
            bare: Boolean,
            right: Boolean,
        ): OverlayGeometry {
            val mini = size == PrototypeWidget.MINI_THREE || size == PrototypeWidget.MINI_TWO
            val slots = if (mini) 1 else size.drops
            val firstColumn = if (right) 4 - slots else 0

            fun column(c: Int) = c * 97.5 + 48.75
            val width = slots * 97.5 - 20
            val height = if (bare) 90.0 else 76.0
            val dropSize =
                when (size) {
                    PrototypeWidget.MINI_TWO -> 32.0
                    PrototypeWidget.MINI_THREE -> 26.0
                    else -> if (bare) 46.0 else 38.0
                }
            val y = if (mini) (if (bare) 154.0 else 168.0) else (if (bare) 156.0 else 169.0)
            val drops =
                (0 until size.drops).map { j ->
                    val x =
                        when (size) {
                            PrototypeWidget.MINI_TWO -> column(firstColumn) + (j - 0.5) * 26
                            PrototypeWidget.MINI_THREE -> column(firstColumn) + (j - 1) * 20
                            else -> column(firstColumn + j)
                        }
                    x to y
                }
            return OverlayGeometry(
                right = right,
                screenWidth = 390.0,
                screenHeight = 844.0,
                widgetLeft = firstColumn * 97.5 + 10,
                widgetTop = 122.0,
                widgetWidth = width,
                widgetHeight = height,
                widgetDrops = drops,
                dropSize = dropSize,
                edgeColumnX = column(if (right) 3 else 0),
                dropY = y,
            )
        }
    }
}

/** The widget sizes the prototype offers ("boyut"). */
public enum class PrototypeWidget(
    public val drops: Int,
) {
    MINI_THREE(3),
    MINI_TWO(2),
    SINGLE(1),
    WIDE_THREE(3),
    WIDE_TWO(2),
}
