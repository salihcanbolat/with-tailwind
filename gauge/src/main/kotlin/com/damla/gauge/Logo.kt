package com.damla.gauge

/** What sits in the middle of the drop (docs/01, "Logolar"). */
public sealed interface Logo {
    /** A round badge with a white rim. Below 14 px it becomes its simple symbol, except Bitcoin. */
    public data class Badge(
        val kind: BadgeKind,
    ) : Logo

    /** A plain symbol in the body colour's contrast colour, for assets without a badge (e.g. Ξ, £, ₺). */
    public data class Text(
        val text: String,
    ) : Logo

    public companion object {
        /** The logo for a badge id of the catalogue (`/v1/assets`). */
        public fun forBadge(id: String): Logo =
            when (id) {
                "au" -> Badge(BadgeKind.GOLD)
                "ag" -> Badge(BadgeKind.SILVER)
                "usd" -> Badge(BadgeKind.USD)
                "eur" -> Badge(BadgeKind.EUR)
                "btc" -> Badge(BadgeKind.BITCOIN)
                "gbp" -> Text("£")
                "eth" -> Text("Ξ")
                "try" -> Text("₺")
                else -> Text(id.take(3).uppercase())
            }
    }
}

public enum class BadgeKind { GOLD, SILVER, USD, EUR, BITCOIN }
