package com.damla.backend.api

import com.damla.api.PriceEvent
import com.damla.backend.state.GroupKey
import com.damla.backend.state.StateRunner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Price events for the targets of [keys], checked every [every]: one whenever a target's price differs
 * from the last one sent, so a quiet market sends nothing (docs/06, "Nabız").
 */
fun priceEvents(
    runner: StateRunner,
    clock: Clock,
    keys: List<GroupKey>,
    every: Duration,
): Flow<PriceEvent> =
    flow {
        val last = HashMap<String, Double>()
        while (true) {
            for (key in keys) {
                val price = runner.price(key) ?: continue
                val previous = last.put(key.target.id, price)
                if (previous == price) continue
                val direction = if (previous == null) 0 else price.compareTo(previous).coerceIn(-1, 1)
                emit(PriceEvent(key.target.id, price, direction, clock.now().toString()))
            }
            delay(every)
        }
    }
