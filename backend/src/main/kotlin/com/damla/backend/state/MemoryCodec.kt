package com.damla.backend.state

import com.damla.engine.AssetMemory
import com.damla.engine.Interval
import com.damla.engine.PartMemory
import com.damla.engine.PartState
import com.damla.engine.Pending
import com.damla.engine.State
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** JSON form of the engine memory, which itself stays free of serialization concerns. */
object MemoryCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(memory: AssetMemory): String =
        json.encodeToString(
            MemoryJson(memory.marketOpen, memory.drop?.toJson(), memory.upperRing?.toJson(), memory.lowerRing?.toJson()),
        )

    fun decode(text: String): AssetMemory {
        val value = json.decodeFromString<MemoryJson>(text)
        return AssetMemory(value.marketOpen, value.drop?.toPart(), value.upperRing?.toPart(), value.lowerRing?.toPart())
    }

    private fun PartMemory.toJson() =
        PartJson(
            state = confirmed.state.name,
            shapeState = confirmed.shapeState.name,
            net = confirmed.net,
            range = confirmed.range,
            intervalMinutes = confirmed.interval.minutes,
            sinceMillis = confirmed.since.toEpochMilliseconds(),
            raw = raw.name,
            pendingState = pending?.state?.name,
            pendingSinceMillis = pending?.since?.toEpochMilliseconds(),
        )

    private fun PartJson.toPart(): PartMemory {
        val pending =
            if (pendingState != null && pendingSinceMillis != null) {
                Pending(State.valueOf(pendingState), Instant.fromEpochMilliseconds(pendingSinceMillis))
            } else {
                null
            }
        return PartMemory(
            confirmed =
                PartState(
                    state = State.valueOf(state),
                    shapeState = State.valueOf(shapeState),
                    net = net,
                    range = range,
                    interval = Interval(intervalMinutes),
                    since = Instant.fromEpochMilliseconds(sinceMillis),
                ),
            raw = State.valueOf(raw),
            pending = pending,
        )
    }

    @Serializable
    private data class MemoryJson(
        val marketOpen: Boolean,
        val drop: PartJson?,
        val upperRing: PartJson?,
        val lowerRing: PartJson?,
    )

    @Serializable
    private data class PartJson(
        val state: String,
        val shapeState: String,
        val net: Double,
        val range: Double,
        val intervalMinutes: Int,
        val sinceMillis: Long,
        val raw: String,
        val pendingState: String? = null,
        val pendingSinceMillis: Long? = null,
    )
}
