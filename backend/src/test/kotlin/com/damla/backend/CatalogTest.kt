package com.damla.backend

import com.damla.backend.catalog.AssetCatalog
import com.damla.backend.catalog.IntervalCodec
import com.damla.backend.catalog.Target
import com.damla.engine.Category
import com.damla.engine.Interval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CatalogTest {
    @Test
    fun `intervals use the short wire format`() {
        assertEquals("15m", IntervalCodec.format(Interval.FIFTEEN_MINUTES))
        assertEquals("1h", IntervalCodec.format(Interval.ONE_HOUR))
        assertEquals("4h", IntervalCodec.format(Interval.FOUR_HOURS))
        assertEquals("1d", IntervalCodec.format(Interval.ONE_DAY))
        assertEquals("1w", IntervalCodec.format(Interval.ONE_WEEK))
        assertEquals("1mo", IntervalCodec.format(Interval.ONE_MONTH))
        assertEquals("37m", IntervalCodec.format(Interval(37)))
        assertEquals("90m", IntervalCodec.format(Interval(90)))
    }

    @Test
    fun `intervals parse back`() {
        for (text in listOf("15m", "1h", "4h", "1d", "1w", "1mo", "37m")) {
            assertEquals(text, IntervalCodec.format(assertNotNull(IntervalCodec.parse(text))))
        }
        assertEquals(Interval(90), IntervalCodec.parse("90m"))
        assertEquals(Interval.ONE_MONTH, IntervalCodec.parse("30d"))
    }

    @Test
    fun `invalid intervals are rejected`() {
        for (text in listOf("", "0m", "15", "m", "1y", "-5m", "1.5h", "15M", "31d", "2mo", "43201m")) {
            assertNull(IntervalCodec.parse(text), text)
        }
    }

    @Test
    fun `targets are listed assets or cross rates`() {
        val single = assertIs<Target.Single>(Target.parse("usdtry"))
        assertEquals(Category.FX, single.category)

        val cross = assertIs<Target.Cross>(Target.parse("btcusdt/xau_gram"))
        assertEquals("btcusdt/xau_gram", cross.id)
        assertEquals(Category.CRYPTO, cross.category)
        assertEquals(listOf("btcusdt", "xau_gram"), cross.components.map { it.id })

        val withLira = assertIs<Target.Cross>(Target.parse("xag_gram/try"))
        assertEquals(listOf("xag_gram"), withLira.components.map { it.id })
    }

    @Test
    fun `invalid targets are rejected`() {
        for (id in listOf("", "nope", "try", "usdtry/usdtry", "usdtry/nope", "a/b/c", "usdtry/")) {
            assertNull(Target.parse(id), id)
        }
    }

    @Test
    fun `the catalog follows the design names`() {
        assertEquals(
            listOf("USD/TRY", "EUR/TRY", "GBP/TRY", "Gram altın", "Çeyrek altın", "Ons altın", "Gümüş", "Bitcoin", "Ethereum"),
            AssetCatalog.assets.map { it.name },
        )
        assertEquals("BTCUSDT", AssetCatalog["btcusdt"]?.symbols?.get("binance"))
        assertEquals("GRA", AssetCatalog["xau_gram"]?.symbols?.get("truncgil"))
    }
}
