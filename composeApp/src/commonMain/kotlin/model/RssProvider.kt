package model

import kotlinx.serialization.Serializable

@Serializable
data class RssProvider(
    val id: String,
    val name: String,
    val url: String
) {
    companion object {
        val ALL_PROVIDERS = listOf(
            RssProvider("coindesk", "CoinDesk", "https://www.coindesk.com/arc/outboundfeeds/rss/"),
            RssProvider("cointelegraph", "CoinTelegraph", "https://cointelegraph.com/rss"),
            RssProvider("decrypt", "Decrypt", "https://decrypt.co/feed"),
            RssProvider("theblock", "The Block", "https://www.theblock.co/rss.xml"),
            RssProvider("cryptoslate", "CryptoSlate", "https://cryptoslate.com/feed/"),
            RssProvider("utoday", "U.Today", "https://u.today/rss"),
            RssProvider("bitcoinmagazine", "Bitcoin Magazine", "https://bitcoinmagazine.com/.rss/full/"),
            RssProvider("beincrypto", "BeInCrypto", "https://beincrypto.com/feed/")
        )
        
        val DEFAULT_ENABLED_PROVIDERS = ALL_PROVIDERS.map { it.id }.toSet()
    }
}
