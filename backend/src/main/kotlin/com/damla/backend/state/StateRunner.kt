package com.damla.backend.state

import com.damla.backend.catalog.AssetCatalog
import com.damla.backend.catalog.AssetInfo
import com.damla.backend.catalog.Currency
import com.damla.backend.catalog.Target
import com.damla.backend.market.MarketCalendar
import com.damla.backend.prices.MarketData
import com.damla.backend.store.StateStore
import com.damla.engine.AssetInput
import com.damla.engine.AssetMemory
import com.damla.engine.AssetStates
import com.damla.engine.Candle
import com.damla.engine.CrossRate
import com.damla.engine.EngineEvent
import com.damla.engine.GaugeEngine
import com.damla.engine.priceWindow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * Runs the engine for every registered group (docs/03, "motor çalıştırıcı") and keeps the latest
 * states and memory. Market data and the calendar are the only inputs; nothing here decides a state.
 */
class StateRunner(
    private val engine: GaugeEngine,
    private val market: MarketData,
    private val calendar: MarketCalendar,
    private val store: StateStore,
) {
    private val mutex = Mutex()
    private val groups = LinkedHashMap<GroupKey, Group>()
    private var stored: Map<String, AssetMemory> = emptyMap()

    private class Group(
        var memory: AssetMemory?,
        var states: AssetStates?,
        var price: Double?,
    )

    data class GroupEvents(
        val key: GroupKey,
        val events: List<EngineEvent>,
    )

    /** Reads saved memories; groups registered afterwards continue from them. */
    suspend fun restore() {
        val loaded = store.loadAll()
        mutex.withLock { stored = loaded }
    }

    /** Adds groups that are not running yet and evaluates them once, so their states are available at once. */
    suspend fun register(
        keys: Collection<GroupKey>,
        now: Instant,
    ) {
        mutex.withLock {
            for (key in keys) {
                if (key in groups) continue
                val group = Group(stored[key.id], states = null, price = null)
                groups[key] = group
                evaluate(key, group, now)
            }
        }
    }

    /** Evaluates every group, saves the memory of groups that reported events and returns the events. */
    suspend fun evaluateAll(now: Instant): List<GroupEvents> {
        val result =
            mutex.withLock {
                groups.mapNotNull { (key, group) ->
                    evaluate(key, group, now).takeIf { it.isNotEmpty() }?.let { GroupEvents(key, it) }
                }
            }
        if (result.isNotEmpty()) save(result.map { it.key }, now)
        return result
    }

    /** Saves the memory of every group. Called periodically so restarts resume close to where they stopped. */
    suspend fun saveAll(now: Instant) = save(mutex.withLock { groups.keys.toList() }, now)

    suspend fun states(key: GroupKey): AssetStates? = mutex.withLock { groups[key]?.states }

    /** The latest price of a target: the asset's own price, or the composed cross rate. */
    suspend fun price(key: GroupKey): Double? = mutex.withLock { groups[key]?.price }

    private suspend fun save(
        keys: List<GroupKey>,
        now: Instant,
    ) {
        val memories = mutex.withLock { keys.mapNotNull { key -> groups[key]?.memory?.let { key.id to it } }.toMap() }
        store.save(memories, now)
    }

    private suspend fun evaluate(
        key: GroupKey,
        group: Group,
        now: Instant,
    ): List<EngineEvent> {
        val input = input(key, now)
        val evaluation = engine.evaluate(key.config, group.memory, input.first)
        group.memory = evaluation.memory
        group.states = evaluation.states
        group.price = input.second
        return evaluation.events
    }

    private suspend fun input(
        key: GroupKey,
        now: Instant,
    ): Pair<AssetInput, Double?> {
        val target = key.target
        val open = marketComponents(target).all { calendar.isOpen(it.category, now) }
        return when (target) {
            is Target.Single -> {
                val id = target.asset.id
                AssetInput(
                    now = now,
                    marketOpen = open,
                    drop = market.window(id, key.drop, now),
                    upperRing = market.window(id, key.upperRing, now),
                    lowerRing = market.window(id, key.lowerRing, now),
                ) to market.quote(id)?.price
            }

            is Target.Cross -> {
                val series = crossSeries(target, now - key.longest.duration)
                AssetInput(
                    now = now,
                    marketOpen = open,
                    drop = priceWindow(series, key.drop, now),
                    upperRing = priceWindow(series, key.upperRing, now),
                    lowerRing = priceWindow(series, key.lowerRing, now),
                ) to series.lastOrNull()?.close
            }
        }
    }

    /**
     * Cross rates compose the two sides minute by minute (docs/03). Sides quoted in different currencies
     * are both brought to TRY first, using USD/TRY for the USD-quoted side.
     */
    private suspend fun crossSeries(
        cross: Target.Cross,
        from: Instant,
    ): List<Candle> {
        val toLira = cross.numerator.currency != cross.denominator.currency
        return CrossRate.compose(side(cross.numerator, from, toLira), side(cross.denominator, from, toLira))
    }

    private suspend fun side(
        asset: AssetInfo,
        from: Instant,
        toLira: Boolean,
    ): List<Candle> {
        if (asset == AssetCatalog.turkishLira) return listOf(LIRA)
        val candles = market.candlesSince(asset.id, from)
        if (!toLira || asset.currency == Currency.TRY) return candles
        val usdTry = market.candlesSince(USD_TRY, from).map(::invert)
        return CrossRate.compose(candles, usdTry)
    }

    companion object {
        private const val USD_TRY = "usdtry"
        private val LIRA = Candle(Instant.DISTANT_PAST, 1.0, 1.0, 1.0, 1.0)

        /** Listed assets whose market must be open for the target to be open, including the USD/TRY conversion. */
        fun marketComponents(target: Target): List<AssetInfo> {
            val sides = target.components
            val needsConversion =
                target is Target.Cross && target.numerator.currency != target.denominator.currency
            return if (needsConversion) sides + listOfNotNull(AssetCatalog[USD_TRY]) else sides
        }

        private fun invert(candle: Candle) = Candle(candle.start, 1 / candle.open, 1 / candle.low, 1 / candle.high, 1 / candle.close)
    }
}
