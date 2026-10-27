package com.damla.backend.devices

import com.damla.backend.catalog.IntervalCodec
import com.damla.backend.catalog.Target
import com.damla.backend.state.GroupKey
import com.damla.engine.Interval
import com.damla.engine.Thresholds
import kotlinx.serialization.Serializable

/** Body of `PUT /v1/devices/{id}` (docs/03). Alarms join in phase 5. */
@Serializable
data class DeviceSettings(
    val fcmToken: String? = null,
    val assets: List<AssetSettings> = emptyList(),
)

/** One watched target. Missing intervals and thresholds fall back to the defaults. */
@Serializable
data class AssetSettings(
    val id: String,
    val drop: String? = null,
    val upperRing: String? = null,
    val lowerRing: String? = null,
    val stable: Double? = null,
    val wave: Double? = null,
)

/** Turns settings into engine groups, or explains what is wrong with them. */
object SettingsResolver {
    const val MAX_ASSETS = 50
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
        return if (errors.isEmpty()) Result.Valid(groups) else Result.Invalid(errors)
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
