package com.damla.backend.devices

import com.damla.alarms.AlarmValidation
import com.damla.api.DeviceSettings
import com.damla.backend.catalog.IntervalCodec
import com.damla.backend.catalog.Target
import com.damla.backend.state.GroupKey
import com.damla.engine.Interval
import com.damla.engine.Thresholds

/** Turns settings into engine groups, or explains what is wrong with them. */
object SettingsResolver {
    const val MAX_ASSETS = 50
    const val MAX_ALARMS = 50
    private val deviceIdPattern = Regex("^[A-Za-z0-9_-]{8,64}$")

    fun isValidDeviceId(id: String): Boolean = deviceIdPattern.matches(id)

    sealed interface Result {
        data class Valid(
            val groups: Map<String, GroupKey>,
        ) : Result

        data class Invalid(
            val errors: List<String>,
        ) : Result
    }

    fun resolve(settings: DeviceSettings): Result {
        val errors = ArrayList<String>()
        if (settings.assets.size > MAX_ASSETS) errors += "at most $MAX_ASSETS assets"
        if (settings.assets
                .map { it.id }
                .toSet()
                .size != settings.assets.size
        ) {
            errors += "duplicate asset ids"
        }
        val groups = LinkedHashMap<String, GroupKey>()
        for (asset in settings.assets) {
            val target = Target.parse(asset.id)
            if (target == null) {
                errors += "unknown asset or invalid cross rate: ${asset.id}"
                continue
            }
            val defaults = GroupKey.default(target)
            val drop = interval(asset.drop, defaults.drop, "${asset.id}.drop", errors)
            val upper = interval(asset.upperRing, defaults.upperRing, "${asset.id}.upperRing", errors)
            val lower = interval(asset.lowerRing, defaults.lowerRing, "${asset.id}.lowerRing", errors)
            val thresholds =
                runCatching {
                    Thresholds(asset.stable ?: defaults.thresholds.stable, asset.wave ?: defaults.thresholds.wave)
                }.getOrElse {
                    errors += "${asset.id}: ${it.message}"
                    null
                }
            if (drop != null && upper != null && lower != null && thresholds != null) {
                groups[target.id] = GroupKey(target, drop, upper, lower, thresholds)
            }
        }
        alarms(settings, groups.keys, errors)
        return if (errors.isEmpty()) Result.Valid(groups) else Result.Invalid(errors)
    }

    /** Alarms must be complete (docs/07) and watch an asset of the device's list, whose settings they use. */
    private fun alarms(
        settings: DeviceSettings,
        watched: Set<String>,
        errors: MutableList<String>,
    ) {
        if (settings.alarms.size > MAX_ALARMS) errors += "at most $MAX_ALARMS alarms"
        if (settings.alarms
                .map { it.id }
                .toSet()
                .size != settings.alarms.size
        ) {
            errors += "duplicate alarm ids"
        }
        for (alarm in settings.alarms) {
            errors += AlarmValidation.problems(alarm)
            val target = Target.parse(alarm.assetId)?.id
            if (target == null || target !in watched) errors += "${alarm.id}: asset ${alarm.assetId} is not in the watch list"
        }
    }

    private fun interval(
        text: String?,
        default: Interval,
        field: String,
        errors: MutableList<String>,
    ): Interval? {
        if (text == null) return default
        return IntervalCodec.parse(text) ?: null.also { errors += "$field: invalid interval '$text'" }
    }
}
