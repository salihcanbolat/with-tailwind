package com.damla.app.overlay

import android.os.Build
import android.view.Window
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Blurs the home screen behind the overlay window (docs/06). Android 12+ and only where the device allows
 * cross-window blur; elsewhere the dim alone darkens the home screen (docs/kararlar.md).
 */
class WindowBlur(
    private val window: Window,
) {
    private var applied = -1

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }

    /** Sets the blur radius in pixels; only whole-pixel changes reach the window manager. */
    fun set(radiusPx: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val radius = radiusPx.roundToInt()
        if (radius == applied) return
        applied = radius
        window.attributes = window.attributes.apply { blurBehindRadius = radius }
    }
}
