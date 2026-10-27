package com.damla.gauge

/**
 * The haptic language (docs/05): every kind of event has its own rhythm, so the user can tell what happened
 * without looking. [timings] alternate pause and vibration in milliseconds, starting with a pause;
 * [amplitudes] are 0–255 for each of them. New prices never vibrate.
 *
 * @property fallback the rhythm for vibrators without amplitude control: the same story told by length
 *   alone (rising short-long, falling long-short).
 */
public enum class Haptic(
    public val timings: LongArray,
    public val amplitudes: IntArray,
    public val fallback: LongArray,
) {
    /** "Yükselişe döndü": tick-TICK, climbing. */
    TURNED_UP(longArrayOf(0, 25, 70, 35), intArrayOf(0, 120, 0, 255), longArrayOf(0, 20, 70, 60)),

    /** "Düşüşe döndü": TICK-tick, descending. */
    TURNED_DOWN(longArrayOf(0, 35, 70, 25), intArrayOf(0, 255, 0, 120), longArrayOf(0, 60, 70, 20)),

    /** "Sabite / dalgalıya döndü": one long soft buzz. */
    CALMED(longArrayOf(0, 260), intArrayOf(0, 80), longArrayOf(0, 260)),

    /** "Fiyat / yüzde alarmı": three equal short ones, unlike any state change. */
    TARGET_REACHED(longArrayOf(0, 30, 70, 30, 70, 30), intArrayOf(0, 200, 0, 200, 0, 200), longArrayOf(0, 30, 70, 30, 70, 30)),

    /** "Piyasa kapandı": one very light touch. */
    MARKET_CLOSED(longArrayOf(0, 20), intArrayOf(0, 60), longArrayOf(0, 20)),
    ;

    public companion object {
        /** The drop turned to [shape] (docs/02, `DropStateChanged`). */
        public fun turnedTo(shape: Shape): Haptic =
            when (shape) {
                Shape.UP -> TURNED_UP
                Shape.DOWN -> TURNED_DOWN
                Shape.FLAT, Shape.WAVE -> CALMED
            }
    }
}
