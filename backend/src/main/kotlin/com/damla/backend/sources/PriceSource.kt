package com.damla.backend.sources

import com.damla.backend.catalog.AssetInfo
import com.damla.backend.prices.PriceUpdate
import com.damla.engine.Candle
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Instant

/** A price provider (docs/03). Each one is adapted to this interface; failover lives in SourceSelector. */
interface PriceSource {
    /** Short identifier, also used as the key of [AssetInfo.symbols]. */
    val name: String

    /** How long the source may stay silent before a backup takes over. */
    val staleAfter: Duration

    fun supports(asset: AssetInfo): Boolean = name in asset.symbols

    /** Live updates for [assets] until cancelled. Implementations reconnect on their own. */
    fun updates(assets: List<AssetInfo>): Flow<PriceUpdate>

    /** Closed one-minute candles starting in [from, to), oldest first. Empty when the source keeps no history. */
    suspend fun history(
        asset: AssetInfo,
        from: Instant,
        to: Instant,
    ): List<Candle>
}
