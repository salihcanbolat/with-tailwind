package com.damla.app.alarms

import android.content.Context
import com.damla.alarms.AlarmText
import com.damla.alarms.AlarmWords
import com.damla.api.AssetSnapshot
import com.damla.app.R
import com.damla.core.Formats
import java.text.NumberFormat

/** The alarm sentences with this app's `alarm_*` strings (docs/07). */
fun alarmText(context: Context): AlarmText {
    val words = AlarmWords { key -> context.getString(TEMPLATES.getValue(key)) }
    return AlarmText(words, ::alarmNumber)
}

/** A value as the user reads it: Turkish separators, at most four decimals, none when whole ("4.300", "2,5"). */
fun alarmNumber(value: Double): String =
    NumberFormat
        .getNumberInstance(Formats.TURKISH)
        .apply { maximumFractionDigits = 4 }
        .format(value)

/** The sign before a price value: ₺, $, € or £; × for cross rates (design/AlarmKur.dc.html). */
fun alarmUnit(asset: AssetSnapshot?): String =
    when (val currency = asset?.currency) {
        null -> "×"
        "TRY" -> "₺"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> "$currency "
    }

/** Every template of [AlarmText.KEYS]; the compiler checks that each one exists. */
private val TEMPLATES: Map<String, Int> =
    mapOf(
        "alarm_summary" to R.string.alarm_summary,
        "alarm_fired" to R.string.alarm_fired,
        "alarm_and" to R.string.alarm_and,
        "alarm_or" to R.string.alarm_or,
        "alarm_value_missing" to R.string.alarm_value_missing,
        "alarm_cond_state" to R.string.alarm_cond_state,
        "alarm_fired_state" to R.string.alarm_fired_state,
        "alarm_part_drop" to R.string.alarm_part_drop,
        "alarm_part_upper_ring" to R.string.alarm_part_upper_ring,
        "alarm_part_lower_ring" to R.string.alarm_part_lower_ring,
        "alarm_target_up" to R.string.alarm_target_up,
        "alarm_target_down" to R.string.alarm_target_down,
        "alarm_target_flat" to R.string.alarm_target_flat,
        "alarm_target_wave" to R.string.alarm_target_wave,
        "alarm_cond_price_up" to R.string.alarm_cond_price_up,
        "alarm_cond_price_down" to R.string.alarm_cond_price_down,
        "alarm_cond_price_both" to R.string.alarm_cond_price_both,
        "alarm_fired_price_up" to R.string.alarm_fired_price_up,
        "alarm_fired_price_down" to R.string.alarm_fired_price_down,
        "alarm_fired_price_both" to R.string.alarm_fired_price_both,
        "alarm_cond_percent_up" to R.string.alarm_cond_percent_up,
        "alarm_cond_percent_down" to R.string.alarm_cond_percent_down,
        "alarm_cond_percent_both" to R.string.alarm_cond_percent_both,
        "alarm_fired_percent_up" to R.string.alarm_fired_percent_up,
        "alarm_fired_percent_down" to R.string.alarm_fired_percent_down,
        "alarm_fired_percent_both" to R.string.alarm_fired_percent_both,
        "alarm_cond_wave" to R.string.alarm_cond_wave,
        "alarm_fired_wave" to R.string.alarm_fired_wave,
        "alarm_period_15m" to R.string.alarm_period_15m,
        "alarm_period_1h" to R.string.alarm_period_1h,
        "alarm_period_1d" to R.string.alarm_period_1d,
        "alarm_period_1w" to R.string.alarm_period_1w,
        "alarm_frequency_once" to R.string.alarm_frequency_once,
        "alarm_frequency_every_time" to R.string.alarm_frequency_every_time,
        "alarm_frequency_hourly" to R.string.alarm_frequency_hourly,
        "alarm_validity_day" to R.string.alarm_validity_day,
        "alarm_validity_week" to R.string.alarm_validity_week,
        "alarm_validity_forever" to R.string.alarm_validity_forever,
        "alarm_card_state" to R.string.alarm_card_state,
        "alarm_card_state_plain" to R.string.alarm_card_state_plain,
        "alarm_card_part_drop" to R.string.alarm_card_part_drop,
        "alarm_card_part_upper_ring" to R.string.alarm_card_part_upper_ring,
        "alarm_card_part_lower_ring" to R.string.alarm_card_part_lower_ring,
        "alarm_from_up" to R.string.alarm_from_up,
        "alarm_from_down" to R.string.alarm_from_down,
        "alarm_from_flat" to R.string.alarm_from_flat,
        "alarm_from_wave" to R.string.alarm_from_wave,
        "alarm_card_price_up" to R.string.alarm_card_price_up,
        "alarm_card_price_down" to R.string.alarm_card_price_down,
        "alarm_card_price_both" to R.string.alarm_card_price_both,
        "alarm_card_percent_up" to R.string.alarm_card_percent_up,
        "alarm_card_percent_down" to R.string.alarm_card_percent_down,
        "alarm_card_percent_both" to R.string.alarm_card_percent_both,
        "alarm_card_wave" to R.string.alarm_card_wave,
        "alarm_kind_state" to R.string.alarm_kind_state,
        "alarm_kind_price" to R.string.alarm_kind_price,
        "alarm_kind_percent" to R.string.alarm_kind_percent,
        "alarm_kind_wave" to R.string.alarm_kind_wave,
        "alarm_kind_mixed" to R.string.alarm_kind_mixed,
    )
