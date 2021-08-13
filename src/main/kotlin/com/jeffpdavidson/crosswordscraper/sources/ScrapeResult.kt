package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.kotwords.formats.Puzzleable

/** Result of a scrape attempt. */
sealed class ScrapeResult {

    /** Result indicating a successful scrape. */
    data class Success(val puzzles: List<Puzzleable> = listOf()) : ScrapeResult()

    /** Result indicating that the user must grant the given permissions before we can perform the scrape. */
    data class NeedPermissions(val permissions: List<String>) : ScrapeResult()

    /** Result indicating an error occurred when scraping. */
    object Error : ScrapeResult()
}