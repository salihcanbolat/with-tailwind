package com.damla.api

import kotlinx.serialization.Serializable

/**
 * Body of `PUT /v1/devices/{id}` (docs/03): the push token, the watch list and the alarms. [order] is the
 * order the phone's widget shows the assets in, so a paired computer shows the same ones first (docs/08).
 * [digest] asks for the evening summary ("Günün özeti"); null means none. `GET /v1/devices/{id}` returns it
 * without the token.
 */
@Serializable
data class DeviceSettings(
    val fcmToken: String? = null,
    val assets: List<AssetSettings> = emptyList(),
    val alarms: List<Alarm> = emptyList(),
    val order: List<String> = emptyList(),
    val digest: DigestSettings? = null,
)

/**
 * When the evening summary comes: at [at] ("HH:mm") in the phone's time zone [zone] (an IANA id such as
 * "Europe/Istanbul"), covering the day since the previous one.
 */
@Serializable
data class DigestSettings(
    val enabled: Boolean = true,
    val at: String = "20:00",
    val zone: String = "Europe/Istanbul",
)

/**
 * One moment of the evening summary: the drop of [asset] (a target id) turned from [from] to [to] at [at]
 * (ISO-8601), while the rings showed [upperRing] and [lowerRing]. States are the engine's names (UP, DOWN,
 * FLAT, WAVE), so the phone draws the gauge exactly as it was.
 */
@Serializable
data class DigestMoment(
    val asset: String,
    val at: String,
    val from: String,
    val to: String,
    val upperRing: String,
    val lowerRing: String,
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
