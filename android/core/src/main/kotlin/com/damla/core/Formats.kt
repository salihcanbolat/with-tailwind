package com.damla.core

import android.content.Context
import com.damla.api.AssetSnapshot
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

/** Turkish display formats. All words come from strings.xml. */
object Formats {
    val TURKISH: Locale = Locale.forLanguageTag("tr-TR")

    /**
     * "son 15 dk", "son 4 saat", "son 1 gün", "son 1 hafta", "son 1 ay" for an interval of the API (`15m`,
     * `4h`, `1d`, `1w`, `1mo`). One day reads "son 1 gün" rather than the prototype's "Dün" (docs/kararlar.md).
     */
    fun interval(
        context: Context,
        wire: String,
    ): String {
        val match = INTERVAL.matchEntire(wire) ?: return wire
        val count = match.groupValues[1].toInt()
        val res =
            when (match.groupValues[2]) {
                "m" -> R.string.interval_minutes
                "h" -> R.string.interval_hours
                "d" -> R.string.interval_days
                "w" -> R.string.interval_weeks
                else -> R.string.interval_months
            }
        return context.getString(res, count)
    }

    /** "1 sa", "1 gün", "1 hf", "15 dk", "1 ay": the short interval labels of the lists. */
    fun intervalShort(
        context: Context,
        wire: String,
    ): String {
        val match = INTERVAL.matchEntire(wire) ?: return wire
        val res =
            when (match.groupValues[2]) {
                "m" -> R.string.interval_short_minutes
                "h" -> R.string.interval_short_hours
                "d" -> R.string.interval_short_days
                "w" -> R.string.interval_short_weeks
                else -> R.string.interval_short_months
            }
        return context.getString(res, match.groupValues[1].toInt())
    }

    /** The signed change in Turkish style: "+%0,12", "−%1,30", "%0,00" (docs/kararlar.md: real sign, no "±"). */
    fun percent(net: Double): String {
        val digits = number(abs(net), 2)
        return when {
            digits == number(0.0, 2) -> "%$digits"
            net > 0 -> "+%$digits"
            else -> "−%$digits"
        }
    }

    /**
     * A price with its currency sign: "₺4.215,30", "$64.120,50". Exchange rates keep four decimals, everything
     * else two (four below 1). Cross rates have no currency.
     */
    fun price(
        asset: AssetSnapshot,
        value: Double,
    ): String {
        val digits =
            when {
                asset.kind == "fx" || asset.kind == "cross" -> 4
                value < 1 -> 4
                else -> 2
            }
        val text = number(value, digits)
        return when (asset.currency) {
            null -> text
            "TRY" -> "₺$text"
            "USD" -> "$$text"
            "EUR" -> "€$text"
            "GBP" -> "£$text"
            else -> "$text ${asset.currency}"
        }
    }

    fun number(
        value: Double,
        digits: Int,
    ): String =
        NumberFormat
            .getNumberInstance(TURKISH)
            .apply {
                minimumFractionDigits = digits
                maximumFractionDigits = digits
            }.format(value)

    /** The subtitle under an asset's name in the detail scene: "Döviz", "Altın · TRY", "Kripto · USD". */
    fun subtitle(
        context: Context,
        asset: AssetSnapshot,
    ): String =
        when (asset.kind) {
            "fx" -> context.getString(R.string.subtitle_fx)
            "crypto" -> context.getString(R.string.subtitle_crypto, asset.currency.orEmpty())
            "cross" -> context.getString(R.string.subtitle_cross)
            else -> context.getString(R.string.subtitle_metal, asset.short, asset.currency.orEmpty())
        }

    /** How fresh an asset's price is (docs/03, "Tazelik"). */
    data class Freshness(
        val text: String,
        val live: Boolean,
        val stale: Boolean,
    )

    fun freshness(
        context: Context,
        asset: AssetSnapshot,
        now: Instant,
    ): Freshness {
        if (!asset.marketOpen) return Freshness(context.getString(R.string.fresh_closed), live = false, stale = false)
        val updated = asset.updatedAt?.let(Instant::parse) ?: return Freshness(context.getString(R.string.fresh_waiting), false, true)
        val age = Duration.between(updated, now).coerceAtLeast(Duration.ZERO)
        return when {
            age >= STALE_AFTER -> Freshness(context.getString(R.string.fresh_stale), live = false, stale = true)
            age.seconds < 2 -> Freshness(context.getString(R.string.fresh_now), live = true, stale = false)
            age.seconds < 60 -> Freshness(context.getString(R.string.fresh_seconds, age.seconds.toInt()), live = true, stale = false)
            else -> Freshness(context.getString(R.string.fresh_minutes, age.toMinutes().toInt()), live = true, stale = false)
        }
    }

    /**
     * Prices are expected at least once a minute (1 minute candles), so twice that marks the data late and the
     * gauge fades (docs/03). Provisional until the widget fade time is decided (docs/kararlar.md).
     */
    val STALE_AFTER: Duration = Duration.ofMinutes(2)

    /** Opacity of a gauge whose data is late (docs/03). */
    const val STALE_ALPHA = 0.6f

    private val INTERVAL = Regex("""^(\d+)(mo|m|h|d|w)$""")
}
