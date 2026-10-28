package com.damla.backend

import com.damla.api.AssetSettings
import com.damla.api.DeviceSettings
import com.damla.api.DigestMoment
import com.damla.api.DigestSettings
import com.damla.backend.config.AppConfig
import com.damla.backend.devices.DeviceRegistry
import com.damla.backend.devices.InMemoryDeviceStore
import com.damla.backend.devices.SettingsResolver
import com.damla.backend.digest.DayLine
import com.damla.backend.digest.DigestRunner
import com.damla.backend.digest.DigestText
import com.damla.backend.digest.DigestWriter
import com.damla.backend.digest.InMemoryDigestStore
import com.damla.backend.push.PushMessage
import com.damla.backend.state.GroupKey
import com.damla.engine.AssetStates
import com.damla.engine.DropStateChanged
import com.damla.engine.Interval
import com.damla.engine.MarketClosed
import com.damla.engine.PartState
import com.damla.engine.Ring
import com.damla.engine.RingStateChanged
import com.damla.engine.State
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class DigestTest {
    /** 20:00 in Istanbul (UTC+3) on Wednesday 28 October 2026. */
    private val due = Instant.parse("2026-10-28T17:00:00Z")
    private val device = "phone-1234"

    private suspend fun setUp(
        writer: DigestWriter? = null,
        digest: DigestSettings? = DigestSettings(at = "20:00", zone = "Europe/Istanbul"),
    ): Triple<DigestRunner, RecordingSender, DeviceRegistry> {
        val registry = DeviceRegistry(InMemoryDeviceStore())
        val settings =
            DeviceSettings(
                "token-1",
                listOf(AssetSettings("usdtry"), AssetSettings("xau_gram"), AssetSettings("xau_gram/xag_gram", name = "Altın/Gümüş")),
                digest = digest,
            )
        registry.put(device, settings, WEDNESDAY_NOON)
        val sender = RecordingSender()
        return Triple(DigestRunner(registry, InMemoryDigestStore(), sender, writer), sender, registry)
    }

    private suspend fun DeviceRegistry.key(target: String): GroupKey = get(device)!!.groups.getValue(target)

    private fun states(
        upper: State,
        lower: State,
    ): AssetStates {
        fun part(state: State) = PartState(state, state, 0.0, 0.0, Interval.FOUR_HOURS, WEDNESDAY_NOON)
        return AssetStates(part(State.UP), part(upper), part(lower))
    }

    @Test
    fun `the day's drop changes go out once at the chosen time, as data for the replay`() =
        runTest {
            val (digests, sender, registry) = setUp()
            val usd = registry.key("usdtry")
            val gold = registry.key("xau_gram")
            // Yesterday before 20:00 belongs to yesterday's summary.
            digests.record(
                usd,
                listOf(DropStateChanged("usdtry", State.UP, State.FLAT, due - 24.hours - 1.minutes)),
                states(State.FLAT, State.UP),
            )
            digests.record(usd, listOf(DropStateChanged("usdtry", State.FLAT, State.UP, due - 8.hours)), states(State.UP, State.FLAT))
            // Ring changes and closures are not drop changes.
            digests.record(
                gold,
                listOf(
                    RingStateChanged("xau_gram", Ring.UPPER, State.FLAT, State.DOWN, due - 7.hours),
                    MarketClosed("xau_gram", due - 7.hours),
                    DropStateChanged("xau_gram", State.UP, State.DOWN, due - 7.hours),
                ),
                states(State.DOWN, State.WAVE),
            )

            digests.sendDue(due - 1.minutes)
            assertEquals(emptyList(), sender.sent)

            digests.sendDue(due + 30.minutes)
            digests.sendDue(due + 31.minutes)

            val message = sender.sent.single()
            assertEquals(PushMessage.Priority.HIGH, message.priority)
            assertEquals("digest", message.data["type"])
            assertEquals("2026-10-28", message.data["day"])
            assertEquals("2", message.data["count"])
            assertEquals("", message.data["text"])
            assertEquals(
                listOf(
                    DigestMoment("usdtry", "2026-10-28T09:00:00Z", "FLAT", "UP", "UP", "FLAT"),
                    DigestMoment("xau_gram", "2026-10-28T10:00:00Z", "UP", "DOWN", "DOWN", "WAVE"),
                ),
                Json.decodeFromString<List<DigestMoment>>(message.data.getValue("moments")),
            )
        }

    @Test
    fun `the writer gets the moments in the phone's time and names, and its sentence goes along`() =
        runTest {
            val asked = mutableListOf<List<DayLine>>()
            val (digests, sender, registry) = setUp(writer = { lines -> "Altın düşüşte kapattı.".also { asked += lines } })
            digests.record(
                registry.key("xau_gram"),
                listOf(DropStateChanged("xau_gram", State.UP, State.DOWN, due - 7.hours)),
                states(State.UP, State.UP),
            )
            digests.record(
                registry.key("xau_gram/xag_gram"),
                listOf(DropStateChanged("xau_gram/xag_gram", State.FLAT, State.WAVE, due - 6.hours)),
                states(State.UP, State.UP),
            )

            digests.sendDue(due)

            assertEquals(
                listOf(DayLine("13:00", "Gram altın", State.UP, State.DOWN), DayLine("14:00", "Altın/Gümüş", State.FLAT, State.WAVE)),
                asked.single(),
            )
            assertEquals("Altın düşüşte kapattı.", sender.sent.single().data["text"])
        }

    @Test
    fun `quiet days, late starts and devices without a summary get nothing`() =
        runTest {
            val (quiet, quietSender, _) = setUp()
            quiet.sendDue(due + 1.minutes)
            assertEquals(emptyList(), quietSender.sent)

            val (late, lateSender, registry) = setUp()
            late.record(
                registry.key("usdtry"),
                listOf(DropStateChanged("usdtry", State.FLAT, State.UP, due - 1.hours)),
                states(State.UP, State.UP),
            )
            late.sendDue(due + 2.hours)
            assertEquals(emptyList(), lateSender.sent)

            val (off, offSender, offRegistry) = setUp(digest = DigestSettings(enabled = false))
            off.record(
                offRegistry.key("usdtry"),
                listOf(DropStateChanged("usdtry", State.FLAT, State.UP, due - 1.hours)),
                states(State.UP, State.UP),
            )
            off.sendDue(due)
            assertEquals(emptyList(), offSender.sent)
        }

    @Test
    fun `the summary's time and zone are checked`() {
        val result =
            SettingsResolver.resolve(
                DeviceSettings(digest = DigestSettings(at = "24:00", zone = "Mars/Olympus")),
            )
        val invalid = assertIs<SettingsResolver.Result.Invalid>(result)
        assertEquals(listOf("digest.at: invalid time '24:00'", "digest.zone: unknown time zone 'Mars/Olympus'"), invalid.errors)
        assertIs<SettingsResolver.Result.Valid>(
            SettingsResolver.resolve(DeviceSettings(digest = DigestSettings(at = "07:30", zone = "UTC"))),
        )
    }

    @Test
    fun `Claude writes the sentence only when switched on`() {
        assertEquals(false, AppConfig.fromEnv(mapOf("ANTHROPIC_API_KEY" to "key")).digestAi)
        assertEquals(true, AppConfig.fromEnv(mapOf("ANTHROPIC_API_KEY" to "key", "DAMLA_DIGEST_AI" to "on")).digestAi)
    }

    @Test
    fun `the prompt lists the moments one per line`() {
        assertEquals(
            "Bugünün anları:\n13:00 Gram altın: UP -> DOWN\n14:05 USD/TRY: FLAT -> WAVE",
            DigestText.prompt(
                listOf(DayLine("13:00", "Gram altın", State.UP, State.DOWN), DayLine("14:05", "USD/TRY", State.FLAT, State.WAVE)),
            ),
        )
    }

    @Test
    fun `only a plain retelling of the moments is kept`() {
        val prompt = DigestText.prompt(listOf(DayLine("13:00", "Gram altın", State.UP, State.DOWN)))

        assertEquals("Gram altın 13:00'te düşüşe geçti.", DigestText.accept("  Gram altın 13:00'te\n düşüşe geçti. ", prompt))
        assertNull(DigestText.accept("", prompt))
        assertNull(DigestText.accept("Gram altın %2 düştü.", prompt))
        assertNull(DigestText.accept("Gram altın 6.400 ₺ oldu.", prompt))
        assertNull(DigestText.accept("Gram altın 16:00'da düştü.", prompt))
        assertNull(DigestText.accept("a".repeat(DigestText.MAX_LENGTH + 1), prompt))
        assertTrue(DigestText.SYSTEM.contains("Never mention prices"))
    }
}
