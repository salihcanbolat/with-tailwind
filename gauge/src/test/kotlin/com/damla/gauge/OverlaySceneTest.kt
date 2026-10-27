package com.damla.gauge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlaySceneTest {
    private val geometry = OverlayGeometry.prototype(PrototypeWidget.SINGLE, bare = true, right = true)

    private fun scene() =
        OverlayScene(
            geometry = geometry,
            assetCount = 4,
            flow = false,
            mini = false,
            bare = true,
            initialChartColor = GaugeColors.UP,
            initialWords = List(3) { GaugeColors.UP },
        )

    @Test
    fun `starts on the widget with a flat chart`() {
        val scene = scene()

        assertEquals(OverlayStage.WIDGET, scene.stage)
        assertEquals(0, scene.selected)
        assertEquals(1, scene.range)
        assertFalse(scene.sharing)
        val frame = scene.frame(0.0)
        assertEquals(List(OverlayScene.CHART_POINTS) { OverlayScene.CHART_HEIGHT / 2 }, frame.chartPoints)
        assertEquals(0.0, frame.dimAlpha)
    }

    @Test
    fun `a stage change keeps the chosen asset unless told otherwise`() {
        val scene = scene()
        scene.show(OverlayStage.DETAIL, 0.0, selected = 2)

        scene.show(OverlayStage.FLOW, 1000.0)

        assertEquals(2, scene.selected)
    }

    @Test
    fun `closing straight from the detail shows the widget drop once the detail gauge has landed`() {
        val scene = scene()
        scene.show(OverlayStage.DETAIL, 0.0, selected = 0)

        scene.show(OverlayStage.WIDGET, 2000.0)

        assertEquals(0.0, scene.frame(2359.0).cells[0].alpha)
        assertTrue(scene.frame(2400.0).cells[0].alpha > 0)
    }

    @Test
    fun `sharing only shows over the detail`() {
        val scene = scene()

        scene.share(true, 0.0)

        assertTrue(scene.sharing)
        assertEquals(0.0, scene.frame(1000.0).shareAlpha)
    }

    @Test
    fun `the chart always has every point`() {
        assertFailsWith<IllegalArgumentException> { scene().setChart(listOf(1.0, 2.0), GaugeColors.UP, emptyList(), 0.0) }
    }
}
