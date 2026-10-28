package com.damla.backend.catalog

import com.damla.engine.Category

/** Quote currency of a price series. */
enum class Currency { TRY, USD }

/**
 * One trackable asset.
 *
 * @property symbols the symbol of this asset at each price source, keyed by source name.
 */
data class AssetInfo(
    val id: String,
    val category: Category,
    val name: String,
    val short: String,
    val badge: String,
    val currency: Currency,
    val symbols: Map<String, String>,
)

/**
 * The v1 catalog (docs/01, names as in design/Ekle and design/CaprazKur).
 *
 * Symbols are filled in per source as adapters are verified against the real API. The `test` source
 * serves every asset with synthetic prices for local development.
 */
object AssetCatalog {
    /** Turkish lira as a cross-rate side (design/CaprazKur). Its price is always 1 and it is not listed alone. */
    val turkishLira = AssetInfo("try", Category.FX, "Türk lirası", "TRY", "try", Currency.TRY, symbols = emptyMap())

    val assets: List<AssetInfo> =
        listOf(
            fx("usdtry", "USD/TRY", "USD", "usd", truncgil = "USD"),
            fx("eurtry", "EUR/TRY", "EUR", "eur", truncgil = "EUR"),
            fx("gbptry", "GBP/TRY", "GBP", "gbp", truncgil = "GBP"),
            metal("xau_gram", "Gram altın", "Altın", Currency.TRY, truncgil = "GRA"),
            metal("xau_ceyrek", "Çeyrek altın", "Çeyrek", Currency.TRY, truncgil = "CEYREKALTIN"),
            // Trunçgil's ounce ("ONS") is 0 in every sample, so the ounce has no source yet.
            metal("xau_ons", "Ons altın", "Ons", Currency.USD, truncgil = null),
            metal("xag_gram", "Gümüş", "Gümüş", Currency.TRY, truncgil = "GUMUS", badge = "ag"),
            crypto("btcusdt", "Bitcoin", "BTC", "btc", binance = "BTCUSDT"),
            crypto("ethusdt", "Ethereum", "ETH", "eth", binance = "ETHUSDT"),
        )

    private val byId = assets.associateBy { it.id }

    /** A listed asset by id. */
    operator fun get(id: String): AssetInfo? = byId[id]

    /** A cross-rate side by id: any listed asset or the Turkish lira. */
    fun side(id: String): AssetInfo? = if (id == turkishLira.id) turkishLira else byId[id]

    private fun fx(
        id: String,
        name: String,
        short: String,
        badge: String,
        truncgil: String,
    ) = AssetInfo(id, Category.FX, name, short, badge, Currency.TRY, symbols = mapOf("truncgil" to truncgil))

    private fun metal(
        id: String,
        name: String,
        short: String,
        currency: Currency,
        truncgil: String?,
        badge: String = "au",
    ) = AssetInfo(id, Category.METAL, name, short, badge, currency, symbols = listOfNotNull(truncgil?.let { "truncgil" to it }).toMap())

    private fun crypto(
        id: String,
        name: String,
        short: String,
        badge: String,
        binance: String,
    ) = AssetInfo(id, Category.CRYPTO, name, short, badge, Currency.USD, symbols = mapOf("binance" to binance))
}
