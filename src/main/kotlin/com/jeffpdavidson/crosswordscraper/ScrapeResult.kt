package com.jeffpdavidson.crosswordscraper

import com.jeffpdavidson.kotwords.model.Crossword

/** Result of a scrape attempt. */
sealed class ScrapeResult {

    /** Result indicating a successful scrape. */
    data class Success(
        val source: String,
        val crossword: Crossword,
        /** Map from FileFormat to a data URL containing the crossword data in that format. */
        val output: Map<FileFormat, String>
    ) : ScrapeResult()

    /** Result indicating that the user must grant the given permissions before we can perform the scrape. */
    data class NeedPermissions(val source: String, val permissions: List<String>) : ScrapeResult()

    /** Result indicating an error occurred when scraping. */
    data class Error(val source: String) : ScrapeResult()
}