package com.damla.gauge

import com.damla.gauge.WidgetArrangement.Kind
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetArrangementTest {
    @Test
    fun `the width picks mini, single or columns`() {
        assertEquals(
            WidgetArrangement(Kind.MINI, 1, 3, bare = true),
            WidgetArrangement.of(80.0, bare = true, single = false, available = 4),
        )
        assertEquals(
            WidgetArrangement(Kind.SINGLE, 1, 1, bare = true),
            WidgetArrangement.of(80.0, bare = true, single = true, available = 4),
        )
        assertEquals(
            WidgetArrangement(Kind.WIDE, 2, 2, bare = false),
            WidgetArrangement.of(160.0, bare = false, single = true, available = 4),
        )
        assertEquals(
            WidgetArrangement(Kind.WIDE, 3, 1, bare = false),
            WidgetArrangement.of(260.0, bare = false, single = false, available = 1),
        )
    }

    @Test
    fun `sizes and rows follow the prototype`() {
        val miniThree = WidgetArrangement(Kind.MINI, 1, 3, bare = true)
        val miniTwo = WidgetArrangement(Kind.MINI, 1, 2, bare = false)
        val wideBare = WidgetArrangement(Kind.WIDE, 3, 3, bare = true)
        val singleBoxed = WidgetArrangement(Kind.SINGLE, 1, 1, bare = false)

        assertEquals(listOf(26.0, 32.0, 46.0, 38.0), listOf(miniThree, miniTwo, wideBare, singleBoxed).map { it.dropSize })
        assertEquals(listOf(32.0, 46.0, 34.0, 47.0), listOf(miniThree, miniTwo, wideBare, singleBoxed).map { it.dropCentreY })
        assertEquals(listOf(90.0, 76.0), listOf(wideBare, singleBoxed).map { it.blockHeight })
        assertEquals(listOf(68.0, 7.0), listOf(wideBare, singleBoxed).map { it.labelTop })
    }

    @Test
    fun `drops are centred in their columns and on the block's row`() {
        val mini = WidgetArrangement(Kind.MINI, 1, 3, bare = true)
        val miniTwo = WidgetArrangement(Kind.MINI, 1, 2, bare = true)
        val wide = WidgetArrangement(Kind.WIDE, 3, 2, bare = false)
        val single = WidgetArrangement(Kind.SINGLE, 1, 1, bare = false)

        assertEquals(listOf(30.0 to 37.0, 50.0 to 37.0, 70.0 to 37.0), mini.dropCentres(100.0, 100.0))
        assertEquals(listOf(37.0 to 37.0, 63.0 to 37.0), miniTwo.dropCentres(100.0, 100.0))
        assertEquals(listOf(50.0 to 59.0, 150.0 to 59.0), wide.dropCentres(300.0, 100.0))
        assertEquals(listOf(50.0 to 59.0), single.dropCentres(100.0, 100.0))
    }

    @Test
    fun `the flow leans on the outermost column`() {
        val wide = WidgetArrangement(Kind.WIDE, 3, 3, bare = true)
        val mini = WidgetArrangement(Kind.MINI, 1, 3, bare = true)

        assertEquals(250.0, wide.edgeColumnX(300.0, right = true))
        assertEquals(50.0, wide.edgeColumnX(300.0, right = false))
        assertEquals(40.0, mini.edgeColumnX(80.0, right = true))
    }

    @Test
    fun `the overlay geometry follows the widget's bounds`() {
        val arrangement = WidgetArrangement(Kind.WIDE, 2, 2, bare = true)

        val geometry = OverlayGeometry.forWidget(400.0, 800.0, left = 200.0, top = 100.0, width = 180.0, height = 110.0, arrangement)

        assertEquals(true, geometry.right)
        assertEquals(listOf(245.0 to 144.0, 335.0 to 144.0), geometry.widgetDrops)
        assertEquals(46.0, geometry.dropSize)
        assertEquals(335.0, geometry.edgeColumnX)
        assertEquals(144.0, geometry.dropY)
        // The widget itself is its 90-tall block, 10 in from the sides of its cells.
        assertEquals(listOf(210.0, 110.0, 160.0, 90.0), with(geometry) { listOf(widgetLeft, widgetTop, widgetWidth, widgetHeight) })
        assertEquals(false, OverlayGeometry.forWidget(400.0, 800.0, 10.0, 100.0, 180.0, 110.0, arrangement).right)
    }
}
