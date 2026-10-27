package com.damla.backend.prices

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Picks which source feeds each asset (docs/03: "kaynak düşerse yedeğe geçer").
 *
 * Sources are tried in preference order; the first one that delivered data within its staleness limit
 * wins, so the primary takes over again as soon as it recovers. When every source is silent (a closed
 * market, or a full outage) the last choice stays, and freshness on the client shows the delay.
 */
class SourceSelector(
    private val preferences: Map<String, List<String>>,
    private val staleAfter: Map<String, Duration>,
) {
    private val lastSeen = HashMap<Pair<String, String>, Instant>()
    private val active = HashMap<String, String>()

    /** Records that [source] delivered data for [asset] at [now] and returns the active source afterwards. */
    fun seen(
        asset: String,
        source: String,
        now: Instant,
    ): String? {
        lastSeen[asset to source] = now
        return select(asset, now)
    }

    fun select(
        asset: String,
        now: Instant,
    ): String? {
        val candidates = preferences[asset].orEmpty()
        val fresh =
            candidates.firstOrNull { source ->
                val seen = lastSeen[asset to source]
                val limit = staleAfter[source]
                seen != null && limit != null && now - seen <= limit
            }
        if (fresh != null) active[asset] = fresh
        return active[asset]
    }

    fun active(asset: String): String? = active[asset]
}
