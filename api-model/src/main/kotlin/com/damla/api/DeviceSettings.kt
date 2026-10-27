package com.damla.api

import kotlinx.serialization.Serializable

/** Body of `PUT /v1/devices/{id}` (docs/03): the push token, the watch list and the alarms. */
@Serializable
data class DeviceSettings(
    val fcmToken: String? = null,
    val assets: List<AssetSettings> = emptyList(),
    val alarms: List<Alarm> = emptyList(),
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
