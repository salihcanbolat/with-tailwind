package com.damla.engine

public enum class Category { FX, METAL, CRYPTO }

/**
 * Stable (s) and wave (d) thresholds in percent (docs/02). The wave threshold must exceed the stable one.
 */
public data class Thresholds(
    val stable: Double,
    val wave: Double,
) {
    init {
        require(stable.isFinite() && stable > 0.0) { "Stable threshold must be positive, was $stable" }
        require(wave.isFinite() && wave > stable) { "Wave threshold ($wave) must exceed stable threshold ($stable)" }
    }

    public companion object {
        /** Starting defaults per category. This is the only place these numbers live. */
        public fun defaultFor(category: Category): Thresholds =
            when (category) {
                Category.FX -> Thresholds(stable = 0.10, wave = 0.30)
                Category.METAL -> Thresholds(stable = 0.15, wave = 0.45)
                Category.CRYPTO -> Thresholds(stable = 0.50, wave = 1.50)
            }
    }
}
