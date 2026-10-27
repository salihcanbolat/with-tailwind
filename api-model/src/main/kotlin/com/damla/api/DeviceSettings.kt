package com.damla.api

import kotlinx.serialization.Serializable

/**
 * Body of `PUT /v1/devices/{id}` (docs/03): the push token, the watch list and the alarms. [order] is the
 * order the phone's widget shows the assets in, so a paired computer shows the same ones first (docs/08).
 * `GET /v1/devices/{id}` returns it without the token.
 */
@Serializable
data class DeviceSettings(
    val fcmToken: String? = null,
    val assets: List<AssetSettings> = emptyList(),
    val alarms: List<Alarm> = emptyList(),
    val order: List<String> = emptyList(),
)

/** `POST /v1/devices/{id}/pairing`: a short code the computer types to follow this phone (docs/08). */
@Serializable
data class PairingCode(
    val code: String,
    val expiresAt: String,
)

/** `POST /v1/pairings/{code}`: the phone the code belongs to. */
@Serializable
data class PairedDevice(
    val deviceId: String,
)

/**
 * One watched target. Missing intervals and thresholds fall back to the defaults. [name] is the user's own
 * name for a cross rate (design/CaprazKur.dc.html, "Ad"); only the apps use it.
 */
@Serializable
data class AssetSettings(
    val id: String,
    val drop: String? = null,
    val upperRing: String? = null,
    val lowerRing: String? = null,
    val stable: Double? = null,
    val wave: Double? = null,
    val name: String? = null,
)
