package com.damla.core

import android.content.Context
import com.damla.api.DigestMoment
import com.damla.gauge.GaugeVisual
import com.damla.gauge.Shape
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * An evening summary as the backend sent it ("Günün özeti", docs/09): the phone's [day], how many targets'
 * drops changed ([count]), Claude's sentence ([text], empty when there is none) and the latest moments.
 */
@Serializable
data class StoredDigest(
    val day: String,
    val count: Int,
    val text: String,
    val moments: List<DigestMoment>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads a `digest` data message; null when it is not one. */
        fun fromMessage(data: Map<String, String>): StoredDigest? {
            val day = data["day"] ?: return null
            val count = data["count"]?.toIntOrNull() ?: return null
            val moments = runCatching { json.decodeFromString<List<DigestMoment>>(data["moments"].orEmpty()) }.getOrNull() ?: return null
            return StoredDigest(day, count, data["text"].orEmpty(), moments)
        }
    }
}

/** The gauge just before (`from`) or just after a moment, with the rings as they were. Only maps the engine's states. */
fun DigestMoment.visual(after: Boolean): GaugeVisual? {
    val drop = Shape.entries.firstOrNull { it.name == if (after) to else from } ?: return null
    val upper = Shape.entries.firstOrNull { it.name == upperRing } ?: return null
    val lower = Shape.entries.firstOrNull { it.name == lowerRing } ?: return null
    return GaugeVisual(drop, upper, lower)
}

/**
 * The phone's own sentence when the backend sends none: how each target ended the day, in the order of their
 * last change ("Gram altın düşüşte, USD/TRY yükselişte."). [name] gives a target's name.
 */
fun StoredDigest.sentence(
    context: Context,
    name: (String) -> String,
): String {
    val last =
        moments
            .groupBy { it.asset }
            .mapValues { (_, list) -> list.last() }
            .values
            .sortedBy { it.at }
    val items =
        last.mapNotNull { moment ->
            val shape = Shape.entries.firstOrNull { it.name == moment.to } ?: return@mapNotNull null
            context.getString(R.string.digest_item, name(moment.asset), context.getString(stateWordLower(shape, closed = false)))
        }
    return context.getString(R.string.digest_sentence, items.joinToString(context.getString(R.string.digest_separator)))
}
