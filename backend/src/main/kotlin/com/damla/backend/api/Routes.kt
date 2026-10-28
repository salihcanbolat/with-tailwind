package com.damla.backend.api

import com.damla.api.AssetSnapshot
import com.damla.api.AssetsResponse
import com.damla.api.CatalogEntry
import com.damla.api.DeviceSettings
import com.damla.api.ErrorResponse
import com.damla.api.HistoryPoint
import com.damla.api.HistoryResponse
import com.damla.api.PairedDevice
import com.damla.api.PairingCode
import com.damla.api.PartSnapshot
import com.damla.api.SnapshotResponse
import com.damla.backend.Services
import com.damla.backend.catalog.AssetCatalog
import com.damla.backend.catalog.AssetInfo
import com.damla.backend.catalog.Currency
import com.damla.backend.catalog.IntervalCodec
import com.damla.backend.catalog.Target
import com.damla.backend.devices.SettingsResolver
import com.damla.backend.state.GroupKey
import com.damla.backend.state.StateRunner
import com.damla.engine.Candle
import com.damla.engine.PartState
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val MAX_SNAPSHOT_ASSETS = 50
private const val DEFAULT_POINTS = 24
private val STREAM_CHECK_EVERY = 1.seconds
private val historyRanges = mapOf("1d" to 1.days, "1w" to 7.days, "1mo" to 30.days, "1y" to 365.days)

/** The v1 API of docs/03. */
fun Route.api(services: Services) {
    get("/v1/assets") {
        call.respond(
            AssetsResponse(
                assets = AssetCatalog.assets.map(::catalogEntry),
                crossSides = (AssetCatalog.assets + AssetCatalog.turkishLira).map(::catalogEntry),
            ),
        )
    }

    get("/v1/snapshot") {
        val ids = list(call.parameters["assets"])
        if (ids.isEmpty() || ids.size > MAX_SNAPSHOT_ASSETS) {
            throw BadRequestException("assets must list 1 to $MAX_SNAPSHOT_ASSETS ids")
        }
        val targets = ids.map { Target.parse(it) ?: throw BadRequestException("unknown asset or invalid cross rate: $it") }
        val device = call.parameters["device"]?.let { services.devices.get(it) }
        val keys = targets.map { device?.groups?.get(it.id) ?: GroupKey.default(it) }
        val now = services.clock.now()
        services.runner.register(keys, now)
        call.respond(SnapshotResponse(now.toString(), keys.map { snapshot(services, it, now) }))
    }

    get("/v1/history") {
        val target =
            call.parameters["asset"]?.let(Target::parse) ?: throw BadRequestException("asset is missing or unknown")
        val rangeName = call.parameters["range"] ?: "1d"
        val range = historyRanges[rangeName] ?: throw BadRequestException("range must be one of ${historyRanges.keys}")
        val points = call.parameters["points"]?.toIntOrNull() ?: DEFAULT_POINTS
        if (points !in 2..500) throw BadRequestException("points must be between 2 and 500")
        val to = services.clock.now()
        val series = history(services, target, to - range, to, points)
        call.respond(
            HistoryResponse(
                asset = target.id,
                range = rangeName,
                currency = (target as? Target.Single)?.asset?.currency?.name,
                points = series.map { (time, price) -> HistoryPoint(time.toString(), price) },
            ),
        )
    }

    // Live prices while the overlay is open (docs/06); the connection ends when the client leaves.
    sse("/v1/stream") {
        val keys = list(call.parameters["assets"]).mapNotNull(Target::parse).take(MAX_SNAPSHOT_ASSETS).map { GroupKey.default(it) }
        services.runner.register(keys, services.clock.now())
        priceEvents(services.runner, services.clock, keys, STREAM_CHECK_EVERY).collect { event ->
            send(ServerSentEvent(data = Json.encodeToString(event), event = "price"))
        }
    }

    // A paired computer reads the phone's watch list, order and alarms; the push token stays private.
    get("/v1/devices/{id}") {
        val device = services.devices.get(call.parameters["id"].orEmpty())
        if (device == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse(listOf("unknown device")))
        } else {
            call.respond(device.settings)
        }
    }

    post("/v1/devices/{id}/pairing") {
        val id = call.parameters["id"].orEmpty()
        if (services.devices.get(id) == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse(listOf("unknown device")))
        } else {
            val (code, expiresAt) = services.pairings.create(id)
            call.respond(PairingCode(code, expiresAt.toString()))
        }
    }

    post("/v1/pairings/{code}") {
        val deviceId = services.pairings.claim(call.parameters["code"].orEmpty())
        if (deviceId == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse(listOf("unknown or expired code")))
        } else {
            call.respond(PairedDevice(deviceId))
        }
    }

    put("/v1/devices/{id}") {
        val id = call.parameters["id"].orEmpty()
        if (!SettingsResolver.isValidDeviceId(id)) throw BadRequestException("device id must be 8 to 64 of A-Z a-z 0-9 _ -")
        val settings = call.receive<DeviceSettings>()
        val now = services.clock.now()
        when (val result = services.devices.put(id, settings, now)) {
            is SettingsResolver.Result.Valid -> {
                services.runner.register(result.groups.values, now)
                call.respond(HttpStatusCode.NoContent)
            }

            is SettingsResolver.Result.Invalid -> {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.errors))
            }
        }
    }
}

private fun list(text: String?): List<String> =
    text
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

private fun catalogEntry(asset: AssetInfo) =
    CatalogEntry(asset.id, asset.category.name.lowercase(), asset.name, asset.short, asset.badge, asset.currency.name)

private suspend fun snapshot(
    services: Services,
    key: GroupKey,
    now: Instant,
): AssetSnapshot {
    val target = key.target
    val components = StateRunner.marketComponents(target)
    val quotes = components.map { services.market.quote(it.id) }
    val states = services.runner.states(key)
    val (kind, name, short, badge, currency) =
        when (target) {
            is Target.Single -> {
                val asset = target.asset
                Labels(asset.category.name.lowercase(), asset.name, asset.short, asset.badge, asset.currency.name)
            }

            is Target.Cross -> {
                val label = "${target.numerator.short}/${target.denominator.short}"
                Labels("cross", label, label, target.numerator.badge, null)
            }
        }
    return AssetSnapshot(
        id = target.id,
        kind = kind,
        name = name,
        short = short,
        badge = badge,
        price = services.runner.price(key),
        currency = currency,
        // A target is only as fresh as its stalest component.
        updatedAt =
            quotes
                .filterNotNull()
                .takeIf { it.size == quotes.size }
                ?.minOfOrNull { it.updatedAt }
                ?.toString(),
        source =
            quotes
                .mapNotNull { it?.source }
                .distinct()
                .joinToString("+")
                .ifEmpty { null },
        marketOpen = components.all { services.calendar.isOpen(it.category, now) },
        drop = states?.drop?.let(::part),
        upperRing = states?.upperRing?.let(::part),
        lowerRing = states?.lowerRing?.let(::part),
        readyAt = if (states == null) readyAt(services, key, components, now)?.toString() else null,
    )
}

/**
 * When the history kept for [components] covers [key]'s longest interval: a window needs a closed candle
 * before its start (engine `priceWindow`). Sources without history (Trunçgil) start from the first price the
 * server saw, so a new server waits this long. Null when a component has no price yet or the time has passed.
 */
private suspend fun readyAt(
    services: Services,
    key: GroupKey,
    components: List<AssetInfo>,
    now: Instant,
): Instant? {
    val firsts = components.map { services.market.firstCandle(it.id) ?: return null }
    val ready = (firsts.maxOrNull() ?: return null) + Candle.LENGTH + key.longest.duration
    return ready.takeIf { it > now }
}

private data class Labels(
    val kind: String,
    val name: String,
    val short: String,
    val badge: String,
    val currency: String?,
)

private fun part(state: PartState) =
    PartSnapshot(
        interval = IntervalCodec.format(state.interval),
        state = state.state.name,
        shapeState = state.shapeState.name,
        net = state.net,
        range = state.range,
        since = state.since.toString(),
    )

/** History of a target from the candle store; cross rates divide the two sides slice by slice. */
private suspend fun history(
    services: Services,
    target: Target,
    from: Instant,
    to: Instant,
    points: Int,
): List<Pair<Instant, Double>> {
    suspend fun closes(id: String) = services.candles.bucketCloses(id, from, to, points).toMap()

    suspend fun side(
        asset: AssetInfo,
        toLira: Boolean,
    ): Map<Instant, Double>? {
        if (asset == AssetCatalog.turkishLira) return null
        val own = closes(asset.id)
        if (!toLira || asset.currency == Currency.TRY) return own
        val usdTry = closes("usdtry")
        return own.mapNotNull { (time, price) -> usdTry[time]?.let { time to price * it } }.toMap()
    }

    return when (target) {
        is Target.Single -> {
            closes(target.asset.id).toList()
        }

        is Target.Cross -> {
            val toLira = target.numerator.currency != target.denominator.currency
            val numerator = side(target.numerator, toLira)
            val denominator = side(target.denominator, toLira)
            val times =
                when {
                    numerator == null -> denominator.orEmpty().keys
                    denominator == null -> numerator.keys
                    else -> numerator.keys intersect denominator.keys
                }
            times.sorted().map { time -> time to (numerator?.getValue(time) ?: 1.0) / (denominator?.getValue(time) ?: 1.0) }
        }
    }
}
