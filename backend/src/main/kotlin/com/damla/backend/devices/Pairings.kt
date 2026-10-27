package com.damla.backend.devices

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Pairing a computer with a phone (docs/08, "Hesap/eşitleme"; there are no accounts yet). The phone asks
 * for a code and shows it; the computer types it once and from then on reads the phone's settings by its
 * device id. Codes live in memory for [ttl] and work once.
 */
class Pairings(
    private val clock: Clock,
    private val random: Random = SecureRandom().asKotlinRandom(),
    private val ttl: Duration = 10.minutes,
) {
    private data class Entry(
        val deviceId: String,
        val expiresAt: Instant,
    )

    private val mutex = Mutex()
    private val codes = HashMap<String, Entry>()

    /** A fresh code for [deviceId]; an earlier unused code of the device stops working. */
    suspend fun create(deviceId: String): Pair<String, Instant> =
        mutex.withLock {
            val now = clock.now()
            codes.entries.removeAll { it.value.expiresAt <= now || it.value.deviceId == deviceId }
            var code = newCode()
            while (code in codes) code = newCode()
            val expiresAt = now + ttl
            codes[code] = Entry(deviceId, expiresAt)
            code to expiresAt
        }

    /** The device of [code], once; null for unknown or expired codes. Case and spaces do not matter. */
    suspend fun claim(code: String): String? =
        mutex.withLock {
            val entry = codes.remove(normalize(code)) ?: return@withLock null
            entry.deviceId.takeIf { entry.expiresAt > clock.now() }
        }

    private fun newCode(): String = String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    companion object {
        const val LENGTH = 6

        /** No 0/O, 1/I/L: easy to read off a phone and type. */
        const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

        fun normalize(code: String): String = code.filterNot { it.isWhitespace() || it == '-' }.uppercase()
    }
}
