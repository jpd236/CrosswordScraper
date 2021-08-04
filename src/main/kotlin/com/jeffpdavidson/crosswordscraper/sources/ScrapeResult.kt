package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.kotwords.formats.Crosswordable
import com.jeffpdavidson.kotwords.model.Puzzle

/** Result of a scrape attempt. */
sealed class ScrapeResult {

    /** Result indicating a successful scrape. */
    data class Success(
        val crosswords: List<Crosswordable> = listOf(),
        val puzzles: List<Puzzle> = listOf(),
        /**
         * Whether we should attempt to convert Puzzles to Crosswords for more format support.
         * TODO: Consider baking this into the Puzzle interface itself.
         */
        val puzzlesAreCrosswordLike: Boolean = false,
    ) : ScrapeResult()

    /** Result indicating that the user must grant the given permissions before we can perform the scrape. */
    data class NeedPermissions(val permissions: List<String>) : ScrapeResult()

    /** Result indicating an error occurred when scraping. */
    object Error : ScrapeResult()
}