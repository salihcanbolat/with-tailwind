package com.damla.backend.devices

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
    )

    /** Loads stored devices and returns every group they follow. Stored settings are re-validated. */
    suspend fun load(): Collection<GroupKey> {
        val loaded = store.loadAll()
        return mutex.withLock {
            for ((id, settings) in loaded) {
                val result = SettingsResolver.resolve(settings)
                if (result is SettingsResolver.Result.Valid) devices[id] = Device(id, settings.fcmToken, result.groups)
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
            mutex.withLock { devices[id] = Device(id, settings.fcmToken, result.groups) }
        }
        return result
    }

    suspend fun get(id: String): Device? = mutex.withLock { devices[id] }

    /** Devices with a push token that follow [key]. */
    suspend fun followers(key: GroupKey): List<Device> =
        mutex.withLock { devices.values.filter { it.token != null && key in it.groups.values } }

    suspend fun forgetToken(id: String) {
        store.clearToken(id)
        mutex.withLock { devices.computeIfPresent(id) { _, device -> device.copy(token = null) } }
    }
}
