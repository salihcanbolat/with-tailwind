package com.damla.core

import kotlinx.serialization.Serializable

/** Widget look (design/KlasorIcerik.dc.html, "Stil"). */
@Serializable
enum class WidgetStyle { BARE, BOXED }

/**
 * What a one-cell widget shows ("Boyut"): three small drops or the first asset alone. Wider widgets always
 * show the wide layout, because on Android the launcher decides the size.
 */
@Serializable
enum class WidgetSize { MINI, WIDE, SINGLE }

/** How a tap opens the widget ("Açılış"). */
@Serializable
enum class WidgetOpening { FLOW, FOLDER }

/**
 * Settings of one widget. [order] lists up to four targets: the first three (one for [WidgetSize.SINGLE])
 * sit in the widget, all of them open in the flow.
 */
@Serializable
data class WidgetConfig(
    val style: WidgetStyle = WidgetStyle.BARE,
    val size: WidgetSize = WidgetSize.MINI,
    val opening: WidgetOpening = WidgetOpening.FLOW,
    val order: List<String> = DEFAULT_ORDER,
) {
    val inWidget: List<String> get() = order.take(if (size == WidgetSize.SINGLE) 1 else 3)

    companion object {
        const val MAX_ASSETS = 4

        /** The assets of the designs, in their order. */
        val DEFAULT_ORDER = listOf("xau_gram", "usdtry", "xag_gram", "btcusdt")
    }
}

/**
 * How often the pulse may play (design/Ayarlar.dc.html, "Nabız"): the shortest time between two pulses of
 * one asset. Provisional values from the prototype's average pulse rates (docs/kararlar.md).
 */
@Serializable
enum class PulseMode(
    val minIntervalMillis: Long?,
) {
    CALM(4_000),
    NORMAL(1_800),
    FAST(800),
    OFF(null),
}

/** App-wide settings (docs/07, Ayarlar). */
@Serializable
data class AppSettings(
    /** "Piyasa kapalıyken gri göster": off keeps the last colours and only adds "piyasa kapalı" to descriptions. */
    val grayWhenClosed: Boolean = true,
    val pulse: PulseMode = PulseMode.NORMAL,
    val vibration: Boolean = true,
)
