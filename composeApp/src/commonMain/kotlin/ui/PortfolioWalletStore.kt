package ui

import model.HyperliquidWallet
import model.MAX_WALLETS
import model.SCOPE_ALL
import model.isValidHyperliquidAddress
import theme.ThemeManager

/**
 * Canonical, stateless mutations for the tracked Hyperliquid wallet list, shared by the
 * Settings UI and [PortfolioViewModel]. Each is a plain `store.update` (mirrors
 * [CryptoViewModel.addToFavorites]); callers launch them in their own scope.
 *
 * Addresses are canonicalised to lowercase so dedup and scope-matching are plain `==`.
 * These intentionally do NOT call `syncSettingsToWidget` — wallet changes don't affect
 * the favorites widget, so reloading its timeline would be wasted work.
 */
private val store get() = ThemeManager.store

/** True when at least one tracked wallet is a valid address (gates the Portfolio tab). */
fun Settings.hasPortfolioWallets(): Boolean =
    hyperliquidWallets.any { isValidHyperliquidAddress(it.address) }

/**
 * Coerce a persisted scope to one that still exists: an unknown single-wallet scope (e.g.
 * the selected wallet was deleted on another device) falls back to the aggregate view.
 */
fun Settings.effectivePortfolioScope(): String =
    if (portfolioScope == SCOPE_ALL || hyperliquidWallets.any { it.address == portfolioScope }) {
        portfolioScope
    } else {
        SCOPE_ALL
    }

/** Add a wallet. No-op when invalid, a duplicate, or the [MAX_WALLETS] cap is reached. */
suspend fun addHyperliquidWallet(rawAddress: String) {
    val addr = rawAddress.trim().lowercase()
    if (!isValidHyperliquidAddress(addr)) return
    store.update { current ->
        val s = current ?: Settings()
        val exists = s.hyperliquidWallets.any { it.address == addr }
        if (exists || s.hyperliquidWallets.size >= MAX_WALLETS) {
            s
        } else {
            s.copy(hyperliquidWallets = s.hyperliquidWallets + HyperliquidWallet(address = addr))
        }
    }
}

/** Set or clear a wallet's manual label (a blank label clears the override). */
suspend fun renameHyperliquidWallet(address: String, label: String?) {
    val addr = address.lowercase()
    val clean = label?.trim()?.takeIf { it.isNotEmpty() }
    store.update { current ->
        val s = current ?: return@update current
        s.copy(
            hyperliquidWallets = s.hyperliquidWallets.map {
                if (it.address == addr) it.copy(customLabel = clean) else it
            },
        )
    }
}

/** Remove a wallet and heal the selected scope back to "All" if it pointed at this wallet. */
suspend fun deleteHyperliquidWallet(address: String) {
    val addr = address.lowercase()
    store.update { current ->
        val s = current ?: return@update current
        s.copy(
            hyperliquidWallets = s.hyperliquidWallets.filterNot { it.address == addr },
            portfolioScope = if (s.portfolioScope == addr) SCOPE_ALL else s.portfolioScope,
        )
    }
}

/** Persist the selected scope: [SCOPE_ALL] or a single lowercased wallet address. */
suspend fun selectPortfolioScope(scope: String) {
    val normalized = if (scope == SCOPE_ALL) SCOPE_ALL else scope.lowercase()
    store.update { current ->
        val s = current ?: return@update current
        if (s.portfolioScope == normalized) s else s.copy(portfolioScope = normalized)
    }
}

/**
 * Write back a resolved ENS name + resolution timestamp for a wallet. Stamp only after a
 * completed HTTP call (even when [ensName] is null) so the TTL gate works; never touches
 * [HyperliquidWallet.customLabel]. No-op when nothing changed.
 */
suspend fun cacheWalletEnsName(address: String, ensName: String?, nowMillis: Long) {
    val addr = address.lowercase()
    store.update { current ->
        val s = current ?: return@update current
        val updated = s.hyperliquidWallets.map {
            if (it.address == addr) it.copy(ensName = ensName, ensResolvedAtMillis = nowMillis) else it
        }
        if (updated == s.hyperliquidWallets) s else s.copy(hyperliquidWallets = updated)
    }
}
