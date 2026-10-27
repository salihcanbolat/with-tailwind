package com.damla.core

import androidx.annotation.StringRes
import com.damla.gauge.Shape

/** The word for a shown state (docs/01). The words live in strings.xml. */
@StringRes
fun stateWord(
    shape: Shape,
    closed: Boolean,
): Int =
    when {
        closed -> R.string.state_closed
        shape == Shape.UP -> R.string.state_up
        shape == Shape.DOWN -> R.string.state_down
        shape == Shape.FLAT -> R.string.state_flat
        else -> R.string.state_wave
    }

/** The lower-case word, for sentences such as "Damla: USD sabit, Altın yükselişte". */
@StringRes
fun stateWordLower(
    shape: Shape,
    closed: Boolean,
): Int =
    when {
        closed -> R.string.state_closed_lower
        shape == Shape.UP -> R.string.state_up_lower
        shape == Shape.DOWN -> R.string.state_down_lower
        shape == Shape.FLAT -> R.string.state_flat_lower
        else -> R.string.state_wave_lower
    }
