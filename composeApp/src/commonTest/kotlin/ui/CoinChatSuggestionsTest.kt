package ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import model.Ticker24hr

/**
 * Unit tests for [suggestionsFor] — the ranking behind the chat's suggestion chips.
 *
 * The point of the ranking is that a coin which just moved 6%, or is sitting at its 24h high, leads
 * with a question about *that* rather than with "What is BTC?" — while the evergreen tail
 * guarantees the user is never shown an empty box.
 */
class CoinChatSuggestionsTest {

    private fun ticker(
        lastPrice: String = "59500.0",
        changePercent: String = "0.4",
        high: String = "60500.0",
        low: String = "58500.0"
    ) = Ticker24hr(
        symbol = "BTCUSDT",
        priceChange = "240.0",
        priceChangePercent = changePercent,
        weightedAvgPrice = "59000.0",
        prevClosePrice = "59260.0",
        lastPrice = lastPrice,
        lastQty = "0.1",
        bidPrice = "59499.0",
        bidQty = "1.0",
        askPrice = "59501.0",
        askQty = "1.0",
        openPrice = "59260.0",
        highPrice = high,
        lowPrice = low,
        volume = "12345.0",
        quoteVolume = "700000000.0",
        openTime = 0L,
        closeTime = 0L
    )

    @Test
    fun aSharpMoveLeadsTheList() {
        val suggestions = suggestionsFor(
            baseAsset = "ETH",
            ticker = ticker(changePercent = "-6.2"),
            hasNews = true,
            hasOverview = true,
            isFirstTurn = true
        )

        assertEquals(ChatSuggestion.WhyMoving, suggestions.first())
    }

    @Test
    fun aQuietDayLeadsWithSomethingElse() {
        val suggestions = suggestionsFor(
            baseAsset = "ETH",
            ticker = ticker(changePercent = "0.4"),
            hasNews = true,
            hasOverview = false,
            isFirstTurn = true
        )

        assertEquals(ChatSuggestion.NewsThemes, suggestions.first())
        // Still offered, just not first.
        assertTrue(ChatSuggestion.WhyMoving in suggestions)
    }

    @Test
    fun sittingAtTheEdgeOfTheDaysRangePromotesTheRangeQuestion() {
        // Last price is 60450 in a 58500-60500 range: inside the top 10%.
        val atHigh = suggestionsFor(
            baseAsset = "ETH",
            ticker = ticker(lastPrice = "60450.0"),
            hasNews = false,
            hasOverview = false,
            isFirstTurn = true
        )
        assertEquals(ChatSuggestion.PriceRange, atHigh.first())

        // Mid-range: demoted to wherever the catalogue puts it. A wide window, because with a
        // hundred questions the default eight-slot one is far too narrow to say "later, not gone".
        val midRange = suggestionsFor(
            baseAsset = "ETH",
            ticker = ticker(lastPrice = "59500.0"),
            hasNews = false,
            hasOverview = false,
            isFirstTurn = true,
            max = 40
        )
        assertTrue(
            midRange.indexOf(ChatSuggestion.PriceRange) > 0,
            "expected the range question to survive but be demoted, got ${midRange.take(5)}"
        )
    }

    @Test
    fun aShortListSpansCategoriesRatherThanExhaustingTheFirst() {
        val suggestions = suggestionsFor(
            baseAsset = "ETH",
            ticker = ticker(),
            hasNews = true,
            hasOverview = true,
            isFirstTurn = true,
            max = MAX_SUGGESTIONS
        )

        // Eight slots drawn from a hundred questions must not all be about the same 24h candle.
        val categories = suggestions.map { it.category }.distinct()
        assertTrue(
            categories.size >= 5,
            "expected the visible handful to span the catalogue, got $categories"
        )
    }

    @Test
    fun theCatalogueOffersAHundredQuestionsGroupedByCategory() {
        val everything = ChatSuggestion.entries.filter { it.category != SuggestionCategory.FollowUp }

        assertEquals(100, everything.size, "the catalogue is meant to hold 100 questions per ticker")
        assertEquals(
            everything.size,
            everything.map { it.res }.distinct().size,
            "two questions share a string resource"
        )
        assertEquals(
            3,
            ChatSuggestion.entries.count { it.category == SuggestionCategory.FollowUp },
            "follow-ups sit outside the hundred"
        )
    }

    @Test
    fun theCatalogueDropsCategoriesItCannotAnswer() {
        val full = suggestionCatalog("ETH", ticker(), hasNews = true, hasOverview = true)
        val flat = full.flatMap { it.suggestions }
        assertEquals(100, flat.size)
        assertEquals(flat.distinct(), flat, "a question appears in two groups")
        assertTrue(full.none { it.suggestions.isEmpty() }, "empty groups should be dropped")

        // No ticker and no news: the market and news categories have nothing answerable left.
        val bare = suggestionCatalog("ETH", ticker = null, hasNews = false, hasOverview = false)
        val bareCategories = bare.map { it.category }
        assertTrue(SuggestionCategory.TodaysMove !in bareCategories)
        assertTrue(SuggestionCategory.NewsCatalysts !in bareCategories)
        // The evergreen categories still stand.
        assertTrue(SuggestionCategory.Fundamentals in bareCategories)
        assertTrue(bare.flatMap { it.suggestions }.none { it.needsTicker || it.needsNews })
    }

    @Test
    fun contextGatedChipsAreOmittedWhenThereIsNoContext() {
        val suggestions = suggestionsFor(
            baseAsset = "ETH",
            ticker = ticker(),
            hasNews = false,
            hasOverview = false,
            isFirstTurn = true
        )

        assertFalse(ChatSuggestion.NewsThemes in suggestions, "nothing to summarise without news")
        assertFalse(ChatSuggestion.ExplainOverview in suggestions, "nothing to explain without an overview")
    }

    @Test
    fun marketChipsAreOmittedWithoutATicker() {
        val suggestions = suggestionsFor(
            baseAsset = "ETH",
            ticker = null,
            hasNews = false,
            hasOverview = false,
            isFirstTurn = true
        )

        assertFalse(ChatSuggestion.WhyMoving in suggestions)
        assertFalse(ChatSuggestion.PriceRange in suggestions)
        assertFalse(ChatSuggestion.VolumeActivity in suggestions)
        // The evergreen tail still fills the list rather than leaving the user an empty box.
        assertTrue(suggestions.size >= 5, "expected the evergreen questions to fill in, got $suggestions")
        assertTrue(ChatSuggestion.WhatIs in suggestions)
    }

    @Test
    fun bitcoinIsNotAskedHowItDiffersFromBitcoin() {
        val btc = suggestionsFor("BTC", ticker(), hasNews = false, hasOverview = false, isFirstTurn = true, max = 100)
        val eth = suggestionsFor("ETH", ticker(), hasNews = false, hasOverview = false, isFirstTurn = true, max = 100)

        assertFalse(ChatSuggestion.VsBitcoin in btc)
        assertTrue(ChatSuggestion.VsBitcoin in eth)
        // The whole comparison group goes, not just the one chip.
        assertFalse(ChatSuggestion.VolatilityVsBitcoin in btc)
        assertFalse(ChatSuggestion.Correlation in btc)

        // And it is withheld from the browsable catalogue too, not only from the short list.
        val btcCatalog = suggestionCatalog("BTC", ticker(), hasNews = false, hasOverview = false)
        assertFalse(ChatSuggestion.VsBitcoin in btcCatalog.flatMap { it.suggestions })
    }

    @Test
    fun onceTheConversationStartsTheChipsBecomeFollowUps() {
        val suggestions = suggestionsFor(
            baseAsset = "ETH",
            ticker = ticker(changePercent = "-6.2"),
            hasNews = true,
            hasOverview = true,
            isFirstTurn = false,
            max = MAX_FOLLOW_UPS
        )

        assertEquals(
            listOf(ChatSuggestion.SimplerPlease, ChatSuggestion.GoDeeper, ChatSuggestion.OneLineSummary),
            suggestions
        )
    }

    @Test
    fun theListIsCappedDeduplicatedAndNeverNegative() {
        val suggestions = suggestionsFor(
            baseAsset = "ETH",
            ticker = ticker(lastPrice = "60450.0", changePercent = "-6.2"),
            hasNews = true,
            hasOverview = true,
            isFirstTurn = true,
            max = MAX_SUGGESTIONS
        )

        assertTrue(suggestions.size <= MAX_SUGGESTIONS)
        assertTrue(suggestions.size >= 5)
        assertEquals(suggestions.distinct(), suggestions, "a promoted chip must not appear twice")

        assertTrue(suggestionsFor("ETH", ticker(), true, true, true, max = 0).isEmpty())
    }
}
