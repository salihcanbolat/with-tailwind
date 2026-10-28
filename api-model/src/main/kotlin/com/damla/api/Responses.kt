package com.damla.api

import kotlinx.serialization.Serializable

/** `GET /v1/snapshot` (docs/03). */
@Serializable
data class SnapshotResponse(
    val serverTime: String,
    val assets: List<AssetSnapshot>,
)

/**
 * One target in a snapshot. Price fields are null until the first price arrives and part states are null
 * until the engine has enough history. [kind] is `fx`, `metal`, `crypto` or `cross`; [currency] is null
 * for cross rates, whose value reads "1 numerator = price denominator".
 */
@Serializable
data class AssetSnapshot(
    val id: String,
    val kind: String,
    val name: String,
    val short: String,
    val badge: String,
    val price: Double?,
    val currency: String?,
    val updatedAt: String?,
    val source: String?,
    val marketOpen: Boolean,
    val drop: PartSnapshot?,
    val upperRing: PartSnapshot?,
    val lowerRing: PartSnapshot?,
    /**
     * While the parts have no states yet because the server's own price history is still shorter than the
     * longest interval: when it will be long enough (ISO-8601). Null otherwise, or when no price came yet.
     */
    val readyAt: String? = null,
)

@Serializable
data class PartSnapshot(
    val interval: String,
    val state: String,
    val shapeState: String,
    val net: Double,
    val range: Double,
    val since: String,
)

/** `GET /v1/assets`: what can be added, and the sides a cross rate can use. */
@Serializable
data class AssetsResponse(
    val assets: List<CatalogEntry>,
    val crossSides: List<CatalogEntry>,
)

@Serializable
data class CatalogEntry(
    val id: String,
    val kind: String,
    val name: String,
    val short: String,
    val badge: String,
    val currency: String,
)

/** `GET /v1/history`: the closing price at the end of each equal slice of the range that has data. */
@Serializable
data class HistoryResponse(
    val asset: String,
    val range: String,
    val currency: String?,
    val points: List<HistoryPoint>,
)

@Serializable
data class HistoryPoint(
    val t: String,
    val p: Double,
)

@Serializable
data class ErrorResponse(
    val errors: List<String>,
)

/**
 * One event of `GET /v1/stream` (server-sent events, event name `price`): the latest price of a target
 * and whether it went up (1), down (-1) or stayed (0) since the previous event. Drives the pulse while
 * the overlay is open (docs/06, "Nabız").
 */
@Serializable
data class PriceEvent(
    val asset: String,
    val price: Double,
    val direction: Int,
    val at: String,
)
