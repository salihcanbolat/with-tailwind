package com.damla.backend.devices

import com.damla.api.Alarm
import com.damla.api.DeviceSettings
import com.damla.backend.state.GroupKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/** Registered devices, indexed by the engine groups they follow. */
class DeviceRegistry(
    private val store: DeviceStore,
) {
    private val mutex = Mutex()
    private val devices = HashMap<String, Device>()

    data class Device(
        val id: String,
        val token: String?,
        val groups: Map<String, GroupKey>,
        val alarms: List<Alarm> = emptyList(),
        val settings: DeviceSettings = DeviceSettings(),
    )

    /** Loads stored devices and returns every group they follow. Stored settings are re-validated. */
    suspend fun load(): Collection<GroupKey> {
        val loaded = store.loadAll()
        return mutex.withLock {
            for ((id, settings) in loaded) {
                val result = SettingsResolver.resolve(settings)
                if (result is SettingsResolver.Result.Valid) devices[id] = device(id, settings, result)
            }
            devices.values.flatMap { it.groups.values }.toSet()
        }
    }

    /** Validates and stores settings. On success returns the device's groups. */
    suspend fun put(
        id: String,
        settings: DeviceSettings,
        now: Instant,
    ): SettingsResolver.Result {
        val result = SettingsResolver.resolve(settings)
        if (result is SettingsResolver.Result.Valid) {
            store.save(id, settings, now)
            mutex.withLock { devices[id] = device(id, settings, result) }
        }
        return result
    }

    suspend fun get(id: String): Device? = mutex.withLock { devices[id] }

    private fun device(
        id: String,
        settings: DeviceSettings,
        result: SettingsResolver.Result.Valid,
    ) = Device(id, settings.fcmToken, result.groups, settings.alarms, settings.copy(fcmToken = null))

    /** Devices that can be told about their alarms: a push token and at least one alarm. */
    suspend fun withAlarms(): List<Device> = mutex.withLock { devices.values.filter { it.token != null && it.alarms.isNotEmpty() } }

    /** Devices that asked for the evening summary and can be sent it. */
    suspend fun withDigest(): List<Device> =
        mutex.withLock {
            devices.values.filter {
                it.token != null &&
                    it.settings.digest?.enabled == true
            }
        }

    /** Devices with a push token that follow [key]. */
    suspend fun followers(key: GroupKey): List<Device> =
        mutex.withLock { devices.values.filter { it.token != null && key in it.groups.values } }

    suspend fun forgetToken(id: String) {
        store.clearToken(id)
        mutex.withLock { devices.computeIfPresent(id) { _, device -> device.copy(token = null) } }
    }
}
