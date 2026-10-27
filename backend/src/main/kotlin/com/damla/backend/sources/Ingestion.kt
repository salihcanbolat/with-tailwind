package com.damla.backend.sources

import com.damla.backend.catalog.AssetCatalog
import com.damla.backend.prices.MarketData
import com.damla.backend.store.CandleStore
import com.damla.engine.Candle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration

/** Moves prices from the sources into memory and the candle store (docs/03, "toplayıcılar"). */
class Ingestion(
    private val sources: List<PriceSource>,
    private val market: MarketData,
    private val store: CandleStore,
    private val clock: Clock,
    private val memory: Duration,
) {
    private val log = LoggerFactory.getLogger(Ingestion::class.java)

    /**
     * Loads stored candles into memory and fills the gap up to now from the first source, in preference
     * order, that has history for the asset.
     */
    suspend fun backfill() {
        val now = clock.now()
        val from = now - memory
        for (asset in AssetCatalog.assets) {
            val stored = store.load(asset.id, from, now)
            market.load(asset.id, stored)
            val missingFrom = stored.lastOrNull()?.let { it.start + Candle.LENGTH } ?: from
            for (source in sources.filter { it.supports(asset) }) {
                val fetched =
                    runCatching { source.history(asset, missingFrom, now) }
                        .onFailure { log.warn("Backfill of {} from {} failed", asset.id, source.name, it) }
                        .getOrDefault(emptyList())
                if (fetched.isEmpty()) continue
                store.save(asset.id, fetched)
                market.load(asset.id, fetched)
                log.info("Backfilled {} candles of {} from {}", fetched.size, asset.id, source.name)
                break
            }
        }
    }

    /** Starts one collector per source. Each source reconnects on its own; this only routes updates. */
    fun start(scope: CoroutineScope): List<Job> =
        sources.map { source ->
            val assets = AssetCatalog.assets.filter(source::supports)
            scope.launch {
                source.updates(assets).collect { update ->
                    val closed = market.accept(update, clock.now())
                    persist(closed)
                }
            }
        }

    suspend fun persist(closed: List<Pair<String, Candle>>) {
        closed.groupBy({ it.first }, { it.second }).forEach { (asset, candles) -> store.save(asset, candles) }
    }

    companion object {
        /** For each listed asset, the configured sources that serve it, in configured order. */
        fun preferences(sources: List<PriceSource>): Map<String, List<String>> =
            AssetCatalog.assets.associate { asset -> asset.id to sources.filter { it.supports(asset) }.map { it.name } }
    }
}
