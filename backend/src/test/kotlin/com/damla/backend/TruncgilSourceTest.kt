package com.damla.backend

import com.damla.backend.catalog.AssetCatalog
import com.damla.backend.prices.PriceUpdate
import com.damla.backend.sources.TruncgilSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** Fixtures are parts of real responses of 2026-10-28, a minute apart (branch samples/truncgil-1). */
class TruncgilSourceTest {
    private val first = resource("today-1.json")
    private val second = resource("today-2.json")
    private val byKey = AssetCatalog.assets.filter { "truncgil" in it.symbols }.associateBy { it.symbols.getValue("truncgil") }

    @Test
    fun `selling prices become ticks at the file's Istanbul time`() {
        val file = TruncgilSource.parse(first, byKey)

        val at = Instant.parse("2026-10-28T16:35:02Z")
        assertEquals(at, file.updated)
        assertEquals(
            listOf(
                "usdtry" to 49.8452,
                "eurtry" to 55.554,
                "gbptry" to 66.3456,
                "xau_gram" to 6473.7,
                "xau_ceyrek" to 10632.48,
                "xag_gram" to 85.99,
            ),
            file.ticks.map { it.asset to it.price },
        )
        assertTrue(file.ticks.all { it.source == "truncgil" && it.time == at })
    }

    @Test
    fun `zero and missing prices are skipped`() {
        val ounce = AssetCatalog["xau_ons"]!!
        val usd = AssetCatalog["usdtry"]!!

        assertEquals(emptyList(), TruncgilSource.parse(first, mapOf("ONS" to ounce, "NOPE" to usd)).ticks)
    }

    @Test
    fun `the ounce has no Truncgil symbol and crypto is left to Binance`() {
        val source = TruncgilSource(HttpClient(MockEngine { respond("") }))

        assertEquals(
            listOf("usdtry", "eurtry", "gbptry", "xau_gram", "xau_ceyrek", "xag_gram"),
            AssetCatalog.assets.filter(source::supports).map { it.id },
        )
    }

    @Test
    fun `only a newer file yields ticks, and unchanged files are asked for with their ETag`() =
        runTest {
            val asked = mutableListOf<String?>()
            val engine =
                MockEngine { request ->
                    asked += request.headers[HttpHeaders.IfNoneMatch]
                    when (asked.size) {
                        1 -> respond(first, HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"a\""))
                        2 -> respond("", HttpStatusCode.NotModified)
                        3 -> respond(first, HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"a2\""))
                        4 -> respond("", HttpStatusCode.ServiceUnavailable)
                        else -> respond(second, HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"b\""))
                    }
                }
            val source = TruncgilSource(HttpClient(engine), pollEvery = 1.milliseconds)

            val updates = source.updates(AssetCatalog.assets).take(12).toList()

            val times = updates.map { (it as PriceUpdate.Tick).time }.distinct()
            assertEquals(listOf(Instant.parse("2026-10-28T16:35:02Z"), Instant.parse("2026-10-28T16:36:02Z")), times)
            assertEquals(listOf(null, "\"a\"", "\"a\"", "\"a2\"", "\"a2\""), asked)
        }

    @Test
    fun `there is no history`() =
        runTest {
            val source = TruncgilSource(HttpClient(MockEngine { respond("") }))
            val gram = AssetCatalog["xau_gram"]!!

            assertEquals(emptyList(), source.history(gram, Instant.parse("2026-10-27T00:00:00Z"), Instant.parse("2026-10-28T00:00:00Z")))
        }

    private fun resource(name: String) = javaClass.getResource("/truncgil/$name")!!.readText()
}
