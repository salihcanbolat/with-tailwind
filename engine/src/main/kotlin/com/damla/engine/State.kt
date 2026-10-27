package com.damla.engine

/**
 * Direction state of one gauge part (docs/02).
 *
 * The drop shows it with shape and colour, the rings with colour only. [CLOSED] appears only while the
 * market is closed; the shape then stays at the last open state (see [PartState.shapeState]).
 */
public enum class State { UP, DOWN, FLAT, WAVE, CLOSED }

public enum class Ring { UPPER, LOWER }
