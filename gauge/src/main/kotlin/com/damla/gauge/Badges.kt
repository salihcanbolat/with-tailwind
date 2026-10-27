package com.damla.gauge

/** The badges of design/Gosterge.dc.html, drawn in their 24 x 24 view box. */
internal object Badges {
    private const val VIEW = 24.0
    private val goldBar = PathData.parse("M4.5,16.8 L19.5,16.8 L16.4,8 L7.6,8 Z")
    private val goldLine = PathData.parse("M8.6,10.7 L15.4,10.7")
    private val silverStack = PathData.parse("M5,8.5 L5,15.5 A7,3 0 0 0 19,15.5 L19,8.5 Z")
    private val silverEdge = PathData.parse("M5,12 A7,3 0 0 0 19,12")
    private val usdStars = listOf(3.0 to 3.0, 7.0 to 3.0, 5.0 to 5.6, 9.0 to 5.6, 3.0 to 8.2, 7.0 to 8.2)
    private val eurStars =
        listOf(
            18.60 to 12.00,
            17.72 to 15.30,
            15.30 to 17.72,
            12.00 to 18.60,
            8.70 to 17.72,
            6.28 to 15.30,
            5.40 to 12.00,
            6.28 to 8.70,
            8.70 to 6.28,
            12.00 to 5.40,
            15.30 to 6.28,
            17.72 to 8.70,
        )

    private fun color(rgb: Long): Int = (0xFF000000 or rgb).toInt()

    /** Draws the badge centred on the origin, with its white rim (`box-shadow` spread). */
    fun draw(
        kind: BadgeKind,
        layout: GaugeLayout,
        canvas: GaugeCanvas,
    ) {
        val diameter = layout.badgePx / layout.unit
        val rim = layout.badgeRingPx / layout.unit
        canvas.fillEllipse(0.0, 0.0, diameter / 2 + rim, diameter / 2 + rim, GaugeColors.WHITE, 0.95)
        canvas.save()
        canvas.clipCircle(0.0, 0.0, diameter / 2)
        canvas.translate(-diameter / 2, -diameter / 2)
        canvas.scale(diameter / VIEW, diameter / VIEW)
        if (layout.smallBadge && kind != BadgeKind.BITCOIN) small(kind, canvas) else full(kind, canvas)
        canvas.restore()
    }

    private fun full(
        kind: BadgeKind,
        canvas: GaugeCanvas,
    ) {
        when (kind) {
            BadgeKind.GOLD -> {
                canvas.fillRect(0.0, 0.0, VIEW, VIEW, color(0xE9B530))
                canvas.fillPath(goldBar, color(0x8A6400))
                canvas.strokePath(goldLine, 1.4, color(0xF7D776))
            }

            BadgeKind.SILVER -> {
                canvas.fillRect(0.0, 0.0, VIEW, VIEW, color(0xCDD2DA))
                canvas.fillPath(silverStack, color(0x4E5661))
                canvas.fillEllipse(12.0, 8.5, 7.0, 3.0, color(0x7A8491))
                canvas.strokeEllipse(12.0, 8.5, 7.0, 3.0, 1.0, color(0xCDD2DA))
                canvas.strokePath(silverEdge, 1.0, color(0xCDD2DA))
            }

            BadgeKind.USD -> {
                canvas.fillRect(0.0, 0.0, VIEW, VIEW, GaugeColors.WHITE)
                for (stripe in 0 until 7) canvas.fillRect(0.0, stripe * 3.7, VIEW, 1.85, color(0xB22234))
                canvas.fillRect(0.0, 0.0, 11.5, 11.1, color(0x3C3B6E))
                for ((x, y) in usdStars) canvas.fillEllipse(x, y, 0.9, 0.9, GaugeColors.WHITE)
            }

            BadgeKind.EUR -> {
                canvas.fillRect(0.0, 0.0, VIEW, VIEW, color(0x003399))
                for ((x, y) in eurStars) canvas.fillEllipse(x, y, 1.15, 1.15, color(0xFFCC00))
            }

            BadgeKind.BITCOIN -> {
                canvas.fillRect(0.0, 0.0, VIEW, VIEW, color(0xF7931A))
                canvas.save()
                canvas.translate(12.0, 12.0)
                canvas.rotate(14.0)
                canvas.translate(-12.0, -12.0)
                canvas.drawText("₿", 12.0, 17.6, 16.0, GaugeColors.WHITE)
                canvas.restore()
            }
        }
    }

    /** Simple symbols for badges under 14 px (docs/01). */
    private fun small(
        kind: BadgeKind,
        canvas: GaugeCanvas,
    ) {
        when (kind) {
            BadgeKind.GOLD -> {
                canvas.fillRect(0.0, 0.0, VIEW, VIEW, color(0xE9B530))
                canvas.fillRect(4.5, 8.5, 15.0, 7.5, color(0x7A5800), cornerRadius = 1.5)
            }

            BadgeKind.USD -> {
                symbol(canvas, color(0x3C3B6E), GaugeColors.WHITE, "$", 17.0, 17.8)
            }

            BadgeKind.SILVER -> {
                symbol(canvas, color(0xCDD2DA), color(0x3B434D), "Ag", 12.5, 16.4)
            }

            else -> {
                symbol(canvas, color(0x003399), color(0xFFCC00), "€", 16.0, 17.6)
            }
        }
    }

    private fun symbol(
        canvas: GaugeCanvas,
        background: Int,
        foreground: Int,
        text: String,
        size: Double,
        baseline: Double,
    ) {
        canvas.fillRect(0.0, 0.0, VIEW, VIEW, background)
        canvas.drawText(text, 12.0, baseline, size, foreground, letterSpacing = -0.5)
    }
}
