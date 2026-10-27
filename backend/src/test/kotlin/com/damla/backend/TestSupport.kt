package com.damla.backend

import com.damla.backend.config.AppConfig
import com.damla.backend.devices.InMemoryDeviceStore
import com.damla.backend.market.MarketCalendar
import com.damla.backend.push.PushMessage
import com.damla.backend.push.PushSender
import com.damla.backend.push.SendResult
import com.damla.backend.sources.PriceSource
import com.damla.backend.sources.TestSource
import com.damla.backend.store.InMemoryCandleStore
import com.damla.backend.store.InMemoryStateStore
import com.damla.engine.Candle
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Wednesday 28 October 2026, 12:00 UTC: every market in the bundled calendar is open. */
val WEDNESDAY_NOON: Instant = Instant.parse("2026-10-28T12:00:00Z")

class MutableClock(
    var now: Instant,
) : Clock {
    override fun now(): Instant = now
}

class RecordingSender(
    var result: SendResult = SendResult.Sent,
) : PushSender {
    val sent = mutableListOf<PushMessage>()

    override suspend fun send(message: PushMessage): SendResult {
        sent += message
        return result
    }
}

/** Flat one-minute candles ending at [end], one per price, oldest first. */
fun candles(
    end: Instant,
    prices: List<Double>,
): List<Candle> =
    prices.mapIndexed { index, price ->
        Candle(end - (prices.size - index).minutes, price, price, price, price)
    }

/** [count] flat candles at [price] ending at [end]. */
fun flatCandles(
    end: Instant,
    count: Int,
    price: Double,
): List<Candle> = candles(end, List(count) { price })

fun testServices(
    clock: Clock,
    sender: PushSender = RecordingSender(),
    sources: List<PriceSource> = listOf(TestSource(clock)),
    calendar: MarketCalendar = MarketCalendar.bundled(),
) = Services(
    config = AppConfig(),
    clock = clock,
    candles = InMemoryCandleStore(),
    states = InMemoryStateStore(),
    deviceStore = InMemoryDeviceStore(),
    sources = sources,
    sender = sender,
    calendar = calendar,
)
