package com.damla.app.ui

import androidx.annotation.StringRes
import com.damla.app.R
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
