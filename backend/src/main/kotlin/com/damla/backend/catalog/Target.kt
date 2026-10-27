package com.damla.backend.catalog

import com.damla.engine.AssetRef
import com.damla.engine.Category
import com.damla.engine.CrossPair

/** What one gauge tracks: a listed asset, or a cross rate written as `numerator/denominator`. */
sealed interface Target {
    val id: String
    val category: Category

    /** Every listed asset whose prices this target needs. */
    val components: List<AssetInfo>

    data class Single(
        val asset: AssetInfo,
    ) : Target {
        override val id: String get() = asset.id
        override val category: Category get() = asset.category
        override val components: List<AssetInfo> get() = listOf(asset)
    }

    data class Cross(
        val numerator: AssetInfo,
        val denominator: AssetInfo,
    ) : Target {
        init {
            CrossPair(AssetRef(numerator.id, numerator.category), AssetRef(denominator.id, denominator.category))
        }

        override val id: String get() = "${numerator.id}/${denominator.id}"
        override val category: Category get() = numerator.category
        override val components: List<AssetInfo>
            get() = listOf(numerator, denominator).filter { it != AssetCatalog.turkishLira }
    }

    companion object {
        /** Parses a target id; null when it names an unknown asset or an invalid cross rate. */
        fun parse(id: String): Target? {
            val parts = id.split('/')
            return when (parts.size) {
                1 -> {
                    AssetCatalog[id]?.let(::Single)
                }

                2 -> {
                    val numerator = AssetCatalog.side(parts[0]) ?: return null
                    val denominator = AssetCatalog.side(parts[1]) ?: return null
                    if (numerator == denominator) null else Cross(numerator, denominator)
                }

                else -> {
                    null
                }
            }
        }
    }
}
