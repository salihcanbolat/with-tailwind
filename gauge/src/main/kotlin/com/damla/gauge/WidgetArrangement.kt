package com.damla.gauge

/**
 * How the home-screen widget lays out its drops (design/Prototip.dc.html). The Glance widget draws with it and
 * the overlay starts its animation from it, so both agree to the pixel. Sizes are dp.
 *
 * The drops sit in a block of fixed height ([blockHeight]) centred vertically in the widget: 90 tall on the
 * wallpaper (Kutusuz), 76 inside the white box (Kutulu), as in the prototype.
 *
 * @property columns equal columns of a wide widget, one per home-screen cell; 1 otherwise.
 * @property drops how many drops are shown.
 */
public data class WidgetArrangement(
    val kind: Kind,
    val columns: Int,
    val drops: Int,
    val bare: Boolean,
) {
    /** Mini: up to three small drops in one cell. Single: one asset. Wide: one drop per column. */
    public enum class Kind { MINI, SINGLE, WIDE }

    public val dropSize: Double
        get() =
            when {
                kind == Kind.MINI -> if (drops == 2) 32.0 else 26.0
                bare -> 46.0
                else -> 38.0
            }

    public val blockHeight: Double get() = if (bare) 90.0 else 76.0

    /** Centre line of the drops from the top of the block. */
    public val dropCentreY: Double
        get() =
            when {
                kind == Kind.MINI -> if (bare) 32.0 else 46.0
                bare -> 34.0
                else -> 47.0
            }

    /** Top of the labels from the top of the block: below the drops on the wallpaper, above them in the box. */
    public val labelTop: Double get() = if (bare) 68.0 else 7.0

    /** Centre distance of the small drops of a mini widget; they overlap slightly. */
    public val miniStep: Double get() = if (drops == 2) 26.0 else 20.0

    /** Drop centres from the widget's top-left corner, for a widget [width] x [height]. */
    public fun dropCentres(
        width: Double,
        height: Double,
    ): List<Pair<Double, Double>> {
        val y = (height - blockHeight) / 2 + dropCentreY
        return (0 until drops).map { j ->
            val x =
                when (kind) {
                    Kind.MINI -> width / 2 + (j - (drops - 1) / 2.0) * miniStep
                    Kind.SINGLE -> width / 2
                    Kind.WIDE -> (j + 0.5) * width / columns
                }
            x to y
        }
    }

    /** Centre of the widget's outermost column on the given side, where the flow leans. */
    public fun edgeColumnX(
        width: Double,
        right: Boolean,
    ): Double =
        when {
            kind != Kind.WIDE -> width / 2
            right -> width - width / columns / 2
            else -> width / columns / 2
        }

    public companion object {
        /** The widths at which the Glance widget switches to two and three columns. */
        public const val TWO_CELLS_WIDTH: Double = 130.0
        public const val THREE_CELLS_WIDTH: Double = 200.0

        /**
         * The arrangement for a widget [width] wide showing [available] assets. A one-cell widget shows the mini
         * layout unless the user chose a single asset; wider widgets always use columns.
         */
        public fun of(
            width: Double,
            bare: Boolean,
            single: Boolean,
            available: Int,
        ): WidgetArrangement =
            when {
                width >= TWO_CELLS_WIDTH -> {
                    val columns = if (width >= THREE_CELLS_WIDTH) 3 else 2
                    WidgetArrangement(Kind.WIDE, columns, minOf(available, columns), bare)
                }

                single -> {
                    WidgetArrangement(Kind.SINGLE, 1, minOf(available, 1), bare)
                }

                else -> {
                    WidgetArrangement(Kind.MINI, 1, minOf(available, 3), bare)
                }
            }
    }
}
