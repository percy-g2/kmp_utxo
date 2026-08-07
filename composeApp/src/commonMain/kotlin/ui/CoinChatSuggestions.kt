package ui

import model.Ticker24hr
import org.jetbrains.compose.resources.StringResource
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.chat_cat_ecosystem
import utxo.composeapp.generated.resources.chat_cat_fundamentals
import utxo.composeapp.generated.resources.chat_cat_learning
import utxo.composeapp.generated.resources.chat_cat_news
import utxo.composeapp.generated.resources.chat_cat_risks
import utxo.composeapp.generated.resources.chat_cat_todays_move
import utxo.composeapp.generated.resources.chat_cat_tokenomics
import utxo.composeapp.generated.resources.chat_cat_trading
import utxo.composeapp.generated.resources.chat_cat_volatility
import utxo.composeapp.generated.resources.chat_cat_volume
import utxo.composeapp.generated.resources.chat_sug_action_meaning
import utxo.composeapp.generated.resources.chat_sug_active_hours
import utxo.composeapp.generated.resources.chat_sug_adoption
import utxo.composeapp.generated.resources.chat_sug_beginner
import utxo.composeapp.generated.resources.chat_sug_burn
import utxo.composeapp.generated.resources.chat_sug_candles
import utxo.composeapp.generated.resources.chat_sug_category
import utxo.composeapp.generated.resources.chat_sug_centralization
import utxo.composeapp.generated.resources.chat_sug_choppy
import utxo.composeapp.generated.resources.chat_sug_community
import utxo.composeapp.generated.resources.chat_sug_competitors
import utxo.composeapp.generated.resources.chat_sug_consensus
import utxo.composeapp.generated.resources.chat_sug_contract_risk
import utxo.composeapp.generated.resources.chat_sug_correlation
import utxo.composeapp.generated.resources.chat_sug_counterparty
import utxo.composeapp.generated.resources.chat_sug_custody
import utxo.composeapp.generated.resources.chat_sug_cycles
import utxo.composeapp.generated.resources.chat_sug_deeper
import utxo.composeapp.generated.resources.chat_sug_depth
import utxo.composeapp.generated.resources.chat_sug_developers
import utxo.composeapp.generated.resources.chat_sug_differentiator
import utxo.composeapp.generated.resources.chat_sug_distribution
import utxo.composeapp.generated.resources.chat_sug_drivers
import utxo.composeapp.generated.resources.chat_sug_failure
import utxo.composeapp.generated.resources.chat_sug_fees
import utxo.composeapp.generated.resources.chat_sug_governance
import utxo.composeapp.generated.resources.chat_sug_history
import utxo.composeapp.generated.resources.chat_sug_how_works
import utxo.composeapp.generated.resources.chat_sug_indicators
import utxo.composeapp.generated.resources.chat_sug_inflation
import utxo.composeapp.generated.resources.chat_sug_interop
import utxo.composeapp.generated.resources.chat_sug_issuance
import utxo.composeapp.generated.resources.chat_sug_jargon
import utxo.composeapp.generated.resources.chat_sug_layer
import utxo.composeapp.generated.resources.chat_sug_liquidity
import utxo.composeapp.generated.resources.chat_sug_liquidity_risk
import utxo.composeapp.generated.resources.chat_sug_market_cap
import utxo.composeapp.generated.resources.chat_sug_max_supply
import utxo.composeapp.generated.resources.chat_sug_metrics
import utxo.composeapp.generated.resources.chat_sug_misconceptions
import utxo.composeapp.generated.resources.chat_sug_momentum
import utxo.composeapp.generated.resources.chat_sug_news
import utxo.composeapp.generated.resources.chat_sug_news_adoption
import utxo.composeapp.generated.resources.chat_sug_news_background
import utxo.composeapp.generated.resources.chat_sug_news_biggest
import utxo.composeapp.generated.resources.chat_sug_news_impact
import utxo.composeapp.generated.resources.chat_sug_news_institutional
import utxo.composeapp.generated.resources.chat_sug_news_regulatory
import utxo.composeapp.generated.resources.chat_sug_news_security
import utxo.composeapp.generated.resources.chat_sug_news_sentiment
import utxo.composeapp.generated.resources.chat_sug_onchain
import utxo.composeapp.generated.resources.chat_sug_one_line
import utxo.composeapp.generated.resources.chat_sug_order_types
import utxo.composeapp.generated.resources.chat_sug_orderbook
import utxo.composeapp.generated.resources.chat_sug_overview
import utxo.composeapp.generated.resources.chat_sug_pair_meaning
import utxo.composeapp.generated.resources.chat_sug_problem
import utxo.composeapp.generated.resources.chat_sug_pullback
import utxo.composeapp.generated.resources.chat_sug_quote_currency
import utxo.composeapp.generated.resources.chat_sug_range
import utxo.composeapp.generated.resources.chat_sug_range_high
import utxo.composeapp.generated.resources.chat_sug_range_low
import utxo.composeapp.generated.resources.chat_sug_range_width
import utxo.composeapp.generated.resources.chat_sug_recovery
import utxo.composeapp.generated.resources.chat_sug_red_flags
import utxo.composeapp.generated.resources.chat_sug_regulatory_risk
import utxo.composeapp.generated.resources.chat_sug_research
import utxo.composeapp.generated.resources.chat_sug_risks
import utxo.composeapp.generated.resources.chat_sug_roadmap
import utxo.composeapp.generated.resources.chat_sug_scams
import utxo.composeapp.generated.resources.chat_sug_simpler
import utxo.composeapp.generated.resources.chat_sug_slippage
import utxo.composeapp.generated.resources.chat_sug_spread
import utxo.composeapp.generated.resources.chat_sug_spread_meaning
import utxo.composeapp.generated.resources.chat_sug_staking
import utxo.composeapp.generated.resources.chat_sug_stats
import utxo.composeapp.generated.resources.chat_sug_supply
import utxo.composeapp.generated.resources.chat_sug_tech
import utxo.composeapp.generated.resources.chat_sug_thin_market
import utxo.composeapp.generated.resources.chat_sug_timeframe
import utxo.composeapp.generated.resources.chat_sug_trend
import utxo.composeapp.generated.resources.chat_sug_unlocks
import utxo.composeapp.generated.resources.chat_sug_use_cases
import utxo.composeapp.generated.resources.chat_sug_vol_meaning
import utxo.composeapp.generated.resources.chat_sug_vol_risk
import utxo.composeapp.generated.resources.chat_sug_vol_unusual
import utxo.composeapp.generated.resources.chat_sug_vol_vs_btc
import utxo.composeapp.generated.resources.chat_sug_volatility
import utxo.composeapp.generated.resources.chat_sug_volume
import utxo.composeapp.generated.resources.chat_sug_volume_confirm
import utxo.composeapp.generated.resources.chat_sug_volume_level
import utxo.composeapp.generated.resources.chat_sug_volume_quote
import utxo.composeapp.generated.resources.chat_sug_vs_avg
import utxo.composeapp.generated.resources.chat_sug_vs_btc
import utxo.composeapp.generated.resources.chat_sug_vs_market
import utxo.composeapp.generated.resources.chat_sug_vs_open
import utxo.composeapp.generated.resources.chat_sug_vs_prev_close
import utxo.composeapp.generated.resources.chat_sug_wallet
import utxo.composeapp.generated.resources.chat_sug_what_is
import utxo.composeapp.generated.resources.chat_sug_where_traded
import utxo.composeapp.generated.resources.chat_sug_who_built
import utxo.composeapp.generated.resources.chat_sug_why_down
import utxo.composeapp.generated.resources.chat_sug_why_up
import utxo.composeapp.generated.resources.chat_sug_yield
import kotlin.math.abs
import kotlin.math.roundToInt

/** Heading a block of related questions in the chat's browsable suggestion list. */
enum class SuggestionCategory(val titleRes: StringResource) {
    TodaysMove(Res.string.chat_cat_todays_move),
    VolumeLiquidity(Res.string.chat_cat_volume),
    VolatilityRange(Res.string.chat_cat_volatility),
    NewsCatalysts(Res.string.chat_cat_news),
    Fundamentals(Res.string.chat_cat_fundamentals),
    Tokenomics(Res.string.chat_cat_tokenomics),
    Risks(Res.string.chat_cat_risks),
    TradingMechanics(Res.string.chat_cat_trading),
    Ecosystem(Res.string.chat_cat_ecosystem),
    Learning(Res.string.chat_cat_learning),

    /** Not part of the browsable catalogue — offered under an answer instead. */
    FollowUp(Res.string.chat_cat_learning)
}

/**
 * A question the chat can offer as a tappable chip. One hundred of them, ten per category, plus
 * three follow-ups.
 *
 * Declaration order is the fill order for [suggestionsFor], so the entries most people want first
 * lead each category.
 *
 * Each entry carries its own [res] rather than being mapped in the UI, which keeps a hundred-branch
 * `when` out of `CoinChatScreen`. Every question string takes the base asset as `%1$s`;
 * [WhyMoving] additionally takes the size of the day's move.
 *
 * The `needs*` flags are availability, not ranking: a question about headlines is not merely a poor
 * suggestion when there are no headlines, it is unanswerable, so it is withheld entirely.
 */
enum class ChatSuggestion(
    val category: SuggestionCategory,
    val res: StringResource,
    /** Unanswerable without live 24h market data. */
    val needsTicker: Boolean = false,
    /** Unanswerable without headlines in the prompt. */
    val needsNews: Boolean = false,
    /** Refers to the overview on the coin screen, so pointless when none was generated. */
    val needsOverview: Boolean = false,
    /** Vacuous on Bitcoin itself. */
    val skipForBitcoin: Boolean = false
) {
    // --- Today's move -------------------------------------------------------
    WhyMoving(SuggestionCategory.TodaysMove, Res.string.chat_sug_why_up, needsTicker = true),
    PriceVsOpen(SuggestionCategory.TodaysMove, Res.string.chat_sug_vs_open, needsTicker = true),
    Trend(SuggestionCategory.TodaysMove, Res.string.chat_sug_trend, needsTicker = true),
    Momentum(SuggestionCategory.TodaysMove, Res.string.chat_sug_momentum, needsTicker = true),
    PriceVsAverage(SuggestionCategory.TodaysMove, Res.string.chat_sug_vs_avg, needsTicker = true),
    Pullback(SuggestionCategory.TodaysMove, Res.string.chat_sug_pullback, needsTicker = true),
    Recovery(SuggestionCategory.TodaysMove, Res.string.chat_sug_recovery, needsTicker = true),
    PriceVsPrevClose(SuggestionCategory.TodaysMove, Res.string.chat_sug_vs_prev_close, needsTicker = true),
    MovesWithMarket(SuggestionCategory.TodaysMove, Res.string.chat_sug_vs_market, needsTicker = true),
    ActionMeaning(SuggestionCategory.TodaysMove, Res.string.chat_sug_action_meaning, needsTicker = true),

    // --- Volume & liquidity -------------------------------------------------
    VolumeActivity(SuggestionCategory.VolumeLiquidity, Res.string.chat_sug_volume, needsTicker = true),
    VolumeLevel(SuggestionCategory.VolumeLiquidity, Res.string.chat_sug_volume_level, needsTicker = true),
    VolumeQuote(SuggestionCategory.VolumeLiquidity, Res.string.chat_sug_volume_quote, needsTicker = true),
    Liquidity(SuggestionCategory.VolumeLiquidity, Res.string.chat_sug_liquidity),
    Spread(SuggestionCategory.VolumeLiquidity, Res.string.chat_sug_spread),
    OrderBookDepth(SuggestionCategory.VolumeLiquidity, Res.string.chat_sug_depth),
    Slippage(SuggestionCategory.VolumeLiquidity, Res.string.chat_sug_slippage),
    VolumeConfirms(SuggestionCategory.VolumeLiquidity, Res.string.chat_sug_volume_confirm, needsTicker = true),
    ThinMarkets(SuggestionCategory.VolumeLiquidity, Res.string.chat_sug_thin_market),
    ActiveHours(SuggestionCategory.VolumeLiquidity, Res.string.chat_sug_active_hours),

    // --- Volatility & range -------------------------------------------------
    Volatility(SuggestionCategory.VolatilityRange, Res.string.chat_sug_volatility, needsTicker = true),
    PriceRange(SuggestionCategory.VolatilityRange, Res.string.chat_sug_range, needsTicker = true),
    RangeWidth(SuggestionCategory.VolatilityRange, Res.string.chat_sug_range_width, needsTicker = true),
    RangeHigh(SuggestionCategory.VolatilityRange, Res.string.chat_sug_range_high, needsTicker = true),
    RangeLow(SuggestionCategory.VolatilityRange, Res.string.chat_sug_range_low, needsTicker = true),
    VolatilityUnusual(SuggestionCategory.VolatilityRange, Res.string.chat_sug_vol_unusual, needsTicker = true),
    VolatilityMeaning(SuggestionCategory.VolatilityRange, Res.string.chat_sug_vol_meaning),
    VolatilityRisk(SuggestionCategory.VolatilityRange, Res.string.chat_sug_vol_risk),
    VolatilityVsBitcoin(SuggestionCategory.VolatilityRange, Res.string.chat_sug_vol_vs_btc, skipForBitcoin = true),
    Choppy(SuggestionCategory.VolatilityRange, Res.string.chat_sug_choppy, needsTicker = true),

    // --- News & catalysts ---------------------------------------------------
    NewsThemes(SuggestionCategory.NewsCatalysts, Res.string.chat_sug_news, needsNews = true),
    ExplainOverview(SuggestionCategory.NewsCatalysts, Res.string.chat_sug_overview, needsOverview = true),
    NewsBiggest(SuggestionCategory.NewsCatalysts, Res.string.chat_sug_news_biggest, needsNews = true),
    NewsSentiment(SuggestionCategory.NewsCatalysts, Res.string.chat_sug_news_sentiment, needsNews = true),
    NewsImpact(SuggestionCategory.NewsCatalysts, Res.string.chat_sug_news_impact, needsNews = true),
    NewsBackground(SuggestionCategory.NewsCatalysts, Res.string.chat_sug_news_background, needsNews = true),
    NewsRegulatory(SuggestionCategory.NewsCatalysts, Res.string.chat_sug_news_regulatory, needsNews = true),
    NewsInstitutional(SuggestionCategory.NewsCatalysts, Res.string.chat_sug_news_institutional, needsNews = true),
    NewsSecurity(SuggestionCategory.NewsCatalysts, Res.string.chat_sug_news_security, needsNews = true),
    NewsAdoption(SuggestionCategory.NewsCatalysts, Res.string.chat_sug_news_adoption, needsNews = true),

    // --- Fundamentals -------------------------------------------------------
    WhatIs(SuggestionCategory.Fundamentals, Res.string.chat_sug_what_is),
    HowItWorks(SuggestionCategory.Fundamentals, Res.string.chat_sug_how_works),
    ProblemSolved(SuggestionCategory.Fundamentals, Res.string.chat_sug_problem),
    UseCases(SuggestionCategory.Fundamentals, Res.string.chat_sug_use_cases),
    WhoBuilt(SuggestionCategory.Fundamentals, Res.string.chat_sug_who_built),
    Consensus(SuggestionCategory.Fundamentals, Res.string.chat_sug_consensus),
    Technology(SuggestionCategory.Fundamentals, Res.string.chat_sug_tech),
    Governance(SuggestionCategory.Fundamentals, Res.string.chat_sug_governance),
    Roadmap(SuggestionCategory.Fundamentals, Res.string.chat_sug_roadmap),
    Differentiator(SuggestionCategory.Fundamentals, Res.string.chat_sug_differentiator),

    // --- Supply & tokenomics ------------------------------------------------
    Supply(SuggestionCategory.Tokenomics, Res.string.chat_sug_supply),
    MaxSupply(SuggestionCategory.Tokenomics, Res.string.chat_sug_max_supply),
    Inflation(SuggestionCategory.Tokenomics, Res.string.chat_sug_inflation),
    Issuance(SuggestionCategory.Tokenomics, Res.string.chat_sug_issuance),
    Distribution(SuggestionCategory.Tokenomics, Res.string.chat_sug_distribution),
    Burn(SuggestionCategory.Tokenomics, Res.string.chat_sug_burn),
    Staking(SuggestionCategory.Tokenomics, Res.string.chat_sug_staking),
    Yield(SuggestionCategory.Tokenomics, Res.string.chat_sug_yield),
    Unlocks(SuggestionCategory.Tokenomics, Res.string.chat_sug_unlocks),
    MarketCap(SuggestionCategory.Tokenomics, Res.string.chat_sug_market_cap),

    // --- Risks & safety -----------------------------------------------------
    KeyRisks(SuggestionCategory.Risks, Res.string.chat_sug_risks),
    Custody(SuggestionCategory.Risks, Res.string.chat_sug_custody),
    Scams(SuggestionCategory.Risks, Res.string.chat_sug_scams),
    RegulatoryRisk(SuggestionCategory.Risks, Res.string.chat_sug_regulatory_risk),
    Centralization(SuggestionCategory.Risks, Res.string.chat_sug_centralization),
    ContractRisk(SuggestionCategory.Risks, Res.string.chat_sug_contract_risk),
    LiquidityRisk(SuggestionCategory.Risks, Res.string.chat_sug_liquidity_risk),
    Counterparty(SuggestionCategory.Risks, Res.string.chat_sug_counterparty),
    FailureModes(SuggestionCategory.Risks, Res.string.chat_sug_failure),
    RedFlags(SuggestionCategory.Risks, Res.string.chat_sug_red_flags),

    // --- Trading & charts ---------------------------------------------------
    ReadOrderBook(SuggestionCategory.TradingMechanics, Res.string.chat_sug_orderbook),
    ExplainStats(SuggestionCategory.TradingMechanics, Res.string.chat_sug_stats, needsTicker = true),
    PairMeaning(SuggestionCategory.TradingMechanics, Res.string.chat_sug_pair_meaning),
    OrderTypes(SuggestionCategory.TradingMechanics, Res.string.chat_sug_order_types),
    ReadCandles(SuggestionCategory.TradingMechanics, Res.string.chat_sug_candles),
    Timeframe(SuggestionCategory.TradingMechanics, Res.string.chat_sug_timeframe),
    Indicators(SuggestionCategory.TradingMechanics, Res.string.chat_sug_indicators),
    SpreadMeaning(SuggestionCategory.TradingMechanics, Res.string.chat_sug_spread_meaning),
    QuoteCurrency(SuggestionCategory.TradingMechanics, Res.string.chat_sug_quote_currency),
    Fees(SuggestionCategory.TradingMechanics, Res.string.chat_sug_fees),

    // --- Ecosystem ----------------------------------------------------------
    VsBitcoin(SuggestionCategory.Ecosystem, Res.string.chat_sug_vs_btc, skipForBitcoin = true),
    Competitors(SuggestionCategory.Ecosystem, Res.string.chat_sug_competitors),
    Category(SuggestionCategory.Ecosystem, Res.string.chat_sug_category),
    Correlation(SuggestionCategory.Ecosystem, Res.string.chat_sug_correlation, skipForBitcoin = true),
    Adoption(SuggestionCategory.Ecosystem, Res.string.chat_sug_adoption),
    Developers(SuggestionCategory.Ecosystem, Res.string.chat_sug_developers),
    Community(SuggestionCategory.Ecosystem, Res.string.chat_sug_community),
    WhereTraded(SuggestionCategory.Ecosystem, Res.string.chat_sug_where_traded),
    Layer(SuggestionCategory.Ecosystem, Res.string.chat_sug_layer),
    Interoperability(SuggestionCategory.Ecosystem, Res.string.chat_sug_interop),

    // --- Learn more ---------------------------------------------------------
    PriceDrivers(SuggestionCategory.Learning, Res.string.chat_sug_drivers),
    Beginner(SuggestionCategory.Learning, Res.string.chat_sug_beginner),
    Jargon(SuggestionCategory.Learning, Res.string.chat_sug_jargon),
    Wallet(SuggestionCategory.Learning, Res.string.chat_sug_wallet),
    OnChain(SuggestionCategory.Learning, Res.string.chat_sug_onchain),
    History(SuggestionCategory.Learning, Res.string.chat_sug_history),
    Cycles(SuggestionCategory.Learning, Res.string.chat_sug_cycles),
    Metrics(SuggestionCategory.Learning, Res.string.chat_sug_metrics),
    Misconceptions(SuggestionCategory.Learning, Res.string.chat_sug_misconceptions),
    Research(SuggestionCategory.Learning, Res.string.chat_sug_research),

    // --- Follow-ups ---------------------------------------------------------
    SimplerPlease(SuggestionCategory.FollowUp, Res.string.chat_sug_simpler),
    GoDeeper(SuggestionCategory.FollowUp, Res.string.chat_sug_deeper),
    OneLineSummary(SuggestionCategory.FollowUp, Res.string.chat_sug_one_line)
}

/** A 24h move at or beyond this promotes "why is it moving?" to the front. */
internal const val SIGNIFICANT_MOVE_PCT = 3.0

/** Within this fraction of either end of the day's range counts as "at the edge". */
internal const val NEAR_EDGE_FRACTION = 0.1

/** What the coin screen's card and the composer's quick row show. */
internal const val MAX_SUGGESTIONS = 8

/** What the composer shows under an answer. */
internal const val MAX_FOLLOW_UPS = 3

/** The three conversational follow-ups, in the order they are offered. */
internal val FOLLOW_UP_SUGGESTIONS = listOf(
    ChatSuggestion.SimplerPlease,
    ChatSuggestion.GoDeeper,
    ChatSuggestion.OneLineSummary
)

/** A category with the questions currently worth asking under it. Empty categories are dropped. */
data class SuggestionGroup(
    val category: SuggestionCategory,
    val suggestions: List<ChatSuggestion>
)

/**
 * Every question answerable right now, grouped by category — what the chat shows on an empty
 * thread, where there is a whole screen to browse rather than one row above the keyboard.
 *
 * Pure and deterministic, so the availability rules are unit-tested rather than eyeballed.
 */
internal fun suggestionCatalog(
    baseAsset: String,
    ticker: Ticker24hr?,
    hasNews: Boolean,
    hasOverview: Boolean
): List<SuggestionGroup> {
    val available = ChatSuggestion.entries.filter {
        it.category != SuggestionCategory.FollowUp &&
            it.isAvailable(baseAsset, ticker, hasNews, hasOverview)
    }
    return SuggestionCategory.entries
        .filter { it != SuggestionCategory.FollowUp }
        .map { category -> SuggestionGroup(category, available.filter { it.category == category }) }
        .filter { it.suggestions.isNotEmpty() }
}

private fun ChatSuggestion.isAvailable(
    baseAsset: String,
    ticker: Ticker24hr?,
    hasNews: Boolean,
    hasOverview: Boolean
): Boolean = when {
    needsTicker && ticker == null -> false
    needsNews && !hasNews -> false
    needsOverview && !hasOverview -> false
    skipForBitcoin && baseAsset.equals("BTC", ignoreCase = true) -> false
    else -> true
}

/**
 * The short, ranked list for places with room for only a handful — the coin screen's card, and the
 * composer once a conversation is under way.
 *
 * Ordering is the whole point: the evergreen questions further down the catalogue guarantee the
 * list is never short, while a coin that just moved 6% or sits at its 24h high leads with a
 * question about that instead of "What is BTC?".
 *
 * @param isFirstTurn false once the conversation has started, which switches to the follow-up set —
 *   "Explain that more simply" only makes sense when there is a "that".
 */
internal fun suggestionsFor(
    baseAsset: String,
    ticker: Ticker24hr?,
    hasNews: Boolean,
    hasOverview: Boolean,
    isFirstTurn: Boolean,
    max: Int = MAX_SUGGESTIONS
): List<ChatSuggestion> {
    if (max <= 0) return emptyList()

    if (!isFirstTurn) {
        return (FOLLOW_UP_SUGGESTIONS + listOf(ChatSuggestion.KeyRisks, ChatSuggestion.PriceDrivers))
            .take(max)
    }

    val changePct = ticker?.priceChangePercent?.toDoubleOrNull()
    val movedSharply = changePct != null && abs(changePct) >= SIGNIFICANT_MOVE_PCT
    val atRangeEdge = ticker != null && isNearRangeEdge(ticker)

    val promoted = buildList {
        // Whatever just happened to the price comes first.
        if (movedSharply) add(ChatSuggestion.WhyMoving)
        if (atRangeEdge) add(ChatSuggestion.PriceRange)
        if (hasNews) add(ChatSuggestion.NewsThemes)
        if (hasOverview) add(ChatSuggestion.ExplainOverview)
    }

    val available = ChatSuggestion.entries.filter {
        it.category != SuggestionCategory.FollowUp &&
            it.isAvailable(baseAsset, ticker, hasNews, hasOverview)
    }

    return (promoted + roundRobinByCategory(available))
        .filter { it.isAvailable(baseAsset, ticker, hasNews, hasOverview) }
        .distinct()
        .take(max)
}

/**
 * Interleaves the catalogue one category at a time, so a short list spans the whole space rather
 * than showing the top of the first category and nothing else.
 *
 * With a hundred questions and room for eight, plain declaration order would fill every slot from
 * "Today's move" — eight ways of asking about the same 24-hour candle, while news, risks and
 * fundamentals never appear. Taking the first of each category, then the second of each, keeps the
 * per-category ordering (each still leads with its most-wanted question) while making the visible
 * handful representative.
 */
private fun roundRobinByCategory(available: List<ChatSuggestion>): List<ChatSuggestion> {
    val byCategory = SuggestionCategory.entries
        .filter { it != SuggestionCategory.FollowUp }
        .map { category -> available.filter { it.category == category } }
        .filter { it.isNotEmpty() }

    if (byCategory.isEmpty()) return emptyList()

    val deepest = byCategory.maxOf { it.size }
    return buildList {
        for (rank in 0 until deepest) {
            byCategory.forEach { group -> group.getOrNull(rank)?.let { add(it) } }
        }
    }
}

/**
 * The size of the day's move as a chip quotes it, without its sign — "4.2" for a 4.23% drop.
 *
 * Rounded to a tenth of a percent deliberately. On the coin screen the ticker arrives over a
 * WebSocket, so the raw figure changes constantly; a chip whose wording ticked with it would be
 * unreadable, and re-deriving the chip list from every frame would make the row re-rank itself
 * while the user is looking at it.
 */
internal fun changeMagnitudeLabel(ticker: Ticker24hr?): String {
    val changePct = ticker?.priceChangePercent?.toDoubleOrNull() ?: 0.0
    return ((abs(changePct) * 10).roundToInt() / 10.0).toString()
}

/** True when the day's move is a fall, which selects the "down" wording for [ChatSuggestion.WhyMoving]. */
internal fun isMoveDown(ticker: Ticker24hr?): Boolean =
    (ticker?.priceChangePercent?.toDoubleOrNull() ?: 0.0) < 0

/**
 * Whether the last price sits in the top or bottom [NEAR_EDGE_FRACTION] of the 24h range.
 *
 * Measured against the range rather than as a percentage of price, so it means the same thing on a
 * coin that moves 0.5% a day as on one that moves 20%.
 */
private fun isNearRangeEdge(ticker: Ticker24hr): Boolean {
    val last = ticker.lastPrice.toDoubleOrNull() ?: return false
    val high = ticker.highPrice.toDoubleOrNull() ?: return false
    val low = ticker.lowPrice.toDoubleOrNull() ?: return false
    val span = high - low
    if (span <= 0.0) return false
    val nearHigh = (high - last) / span <= NEAR_EDGE_FRACTION
    val nearLow = (last - low) / span <= NEAR_EDGE_FRACTION
    return nearHigh || nearLow
}
