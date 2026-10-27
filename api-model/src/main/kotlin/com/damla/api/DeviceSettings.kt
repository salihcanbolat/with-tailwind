package com.damla.api

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
