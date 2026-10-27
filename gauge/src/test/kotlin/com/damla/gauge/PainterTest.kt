package com.damla.gauge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PainterTest {
    private val visual = GaugeVisual(Shape.UP, upperRing = Shape.WAVE, lowerRing = Shape.DOWN)

    private fun paint(
        motion: GaugeMotion,
        at: Double = 0.0,
        size: Double = 120.0,
        logo: Logo? = Logo.Badge(BadgeKind.GOLD),
    ): List<String> = RecordingCanvas().also { GaugePainter.paint(motion.frame(at), GaugeLayout(size), logo, it) }.calls

    @Test
    fun `a drop at rest draws rings, body, light, badge and highlight in order`() {
        val calls = paint(GaugeMotion(visual))

        val drawn = calls.filter { it.startsWith("arc") || it.startsWith("fillPath") || it.startsWith("gradient") || it.startsWith("rect") }
        assertEquals("arc #ff9500 r=40.0 sweep=-69.8", drawn[0])
        assertEquals("arc #ff3b30 r=48.0 sweep=-60.0", drawn[1])
        assertEquals("fillPath #34c759", drawn[2])
        assertEquals("arc #ffffff r=18.5 sweep=70.0", drawn[3])
        assertEquals("rect #e9b530", drawn[4])
        assertEquals("gradient #ffffff 0.26", drawn.last())
        assertFalse(calls.any { it.startsWith("layer") }, "no layer at full opacity")
        assertEquals(calls.count { it == "save" || it.startsWith("layer") }, calls.count { it == "restore" })
    }

    @Test
    fun `flat shows the circle instead of the drop`() {
        val calls = paint(GaugeMotion(visual.copy(shape = Shape.FLAT)))

        assertFalse(calls.any { it.startsWith("fillPath #ffcc00") })
        assertTrue(calls.contains("ellipse #ffcc00 25.0"))
    }

    @Test
    fun `fading drop and circle are drawn as layers, with the halo behind`() {
        val motion = GaugeMotion(visual)
        motion.update(visual.copy(shape = Shape.FLAT), 0.0)

        val calls = paint(motion, at = 100.0)

        assertTrue(calls.first { it.startsWith("gradient") }.startsWith("gradient #ffcc00"), "halo comes first")
        assertEquals(2, calls.count { it.startsWith("layer") })
    }

    @Test
    fun `a ring waiting for its redraw leaves a dot at its end`() {
        val motion = GaugeMotion(visual)
        motion.update(visual.copy(upperRing = Shape.UP), 0.0)

        val calls = paint(motion, at = 100.0)

        assertTrue(calls.any { it.startsWith("ellipse") && it.endsWith(" 2.75") })
        assertFalse(calls.any { it.startsWith("arc") && it.contains("r=40.0") })
    }

    @Test
    fun `text logos and missing logos`() {
        val text = paint(GaugeMotion(visual.copy(shape = Shape.FLAT)), logo = Logo.Text("GBP"))
        assertTrue(text.contains("centeredText GBP #5a4300 spacing=-0.70"), text.toString())

        val single = paint(GaugeMotion(visual), logo = Logo.Text("Ξ"))
        assertTrue(single.contains("centeredText Ξ #ffffff spacing=0.00"))

        val none = paint(GaugeMotion(visual), logo = null)
        assertFalse(none.any { it.startsWith("rect") || it.startsWith("centeredText") })
    }

    @Test
    fun `every badge in full and small size`() {
        for (kind in BadgeKind.entries) {
            for (size in listOf(120.0, 32.0)) {
                val calls = paint(GaugeMotion(visual), size = size, logo = Logo.Badge(kind))
                assertTrue(calls.contains("clip"), "$kind $size is clipped to a circle")
                assertTrue(calls.any { it.startsWith("ellipse #ffffff") }, "$kind $size has a rim")
            }
        }
        assertTrue(paint(GaugeMotion(visual), size = 32.0, logo = Logo.Badge(BadgeKind.USD)).contains("text $"))
        assertTrue(paint(GaugeMotion(visual), size = 32.0, logo = Logo.Badge(BadgeKind.SILVER)).contains("text Ag"))
        assertTrue(paint(GaugeMotion(visual), size = 32.0, logo = Logo.Badge(BadgeKind.EUR)).contains("text €"))
        assertTrue(
            paint(GaugeMotion(visual), size = 32.0, logo = Logo.Badge(BadgeKind.BITCOIN)).contains("text ₿"),
            "Bitcoin has no small variant",
        )
        assertTrue(paint(GaugeMotion(visual), logo = Logo.Badge(BadgeKind.SILVER)).contains("strokeEllipse #cdd2da"))
    }

    @Test
    fun `catalogue badges map to logos`() {
        assertEquals(
            listOf(
                Logo.Badge(BadgeKind.GOLD),
                Logo.Badge(BadgeKind.SILVER),
                Logo.Badge(BadgeKind.USD),
                Logo.Badge(BadgeKind.EUR),
                Logo.Badge(BadgeKind.BITCOIN),
                Logo.Text("£"),
                Logo.Text("Ξ"),
                Logo.Text("₺"),
                Logo.Text("XYZ"),
            ),
            listOf("au", "ag", "usd", "eur", "btc", "gbp", "eth", "try", "xyzw").map(Logo::forBadge),
        )
    }

    @Test
    fun `svg output is well formed`() {
        val motion = GaugeMotion(visual)
        motion.update(visual.copy(shape = Shape.FLAT, pulseTurns = 1), 0.0)
        val canvas = SvgCanvas(120.0, 145.0)

        GaugePainter.paint(motion.frame(200.0), GaugeLayout(120.0), Logo.Text("A<&"), canvas)
        val svg = canvas.toSvg()

        javax.xml.parsers.DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(svg.byteInputStream())
        assertTrue(svg.contains("radialGradient"))
        assertTrue(svg.contains("clipPath").not())
        assertTrue(svg.contains("A&lt;&amp;"))
        for (kind in BadgeKind.entries) {
            val badge = SvgCanvas(120.0, 145.0)
            GaugePainter.paint(GaugeMotion(visual).frame(0.0), GaugeLayout(120.0), Logo.Badge(kind), badge)
            javax.xml.parsers.DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(badge.toSvg().byteInputStream())
        }
        val small = SvgCanvas(32.0, 39.0)
        GaugePainter.paint(GaugeMotion(visual).frame(0.0), GaugeLayout(32.0), Logo.Badge(BadgeKind.GOLD), small)
        assertTrue(small.toSvg().contains("rx=\"1.5\""))
        assertEquals("0.1235", SvgCanvas.n(0.123456))
        assertEquals("3", SvgCanvas.n(3.0))
    }
}
