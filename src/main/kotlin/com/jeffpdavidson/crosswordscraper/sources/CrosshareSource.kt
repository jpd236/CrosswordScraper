package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.AcrossLite
import com.jeffpdavidson.kotwords.formats.Crosshare
import com.jeffpdavidson.kotwords.formats.Crosswordable
import org.w3c.dom.url.URL

object CrosshareSource : Source {

    private val PUZZLE_ID_PATTERN = "crosswords/([^/]+)".toRegex()

    override val sourceName = "Crosshare"
    override val neededHostPermissions = listOf("https://*.crosshare.org/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("crosshare.org") &&
                (url.pathname.startsWith("/crosswords/") || (url.pathname.startsWith("/embed/")))
    }

    override suspend fun scrapePuzzle(url: URL, frameId: Int): Crosswordable? {
        // First, try to scrape from the __NEXT_DATA__ JSON on the current page. This works for direct links to
        // Crosshare puzzles, but may contain other data if the user navigated to this page from another Crosshare
        // page.
        val puzzleJson = Scraping.readGlobalJson(frameId, "__NEXT_DATA__")
        if (puzzleJson.isNotEmpty()) {
            try {
                val crosshare = Crosshare(puzzleJson)
                val crossword = crosshare.asCrossword()
                // Make sure the puzzle title matches the page title - otherwise, this data is probably for a different
                // puzzle.
                val pageTitle = Scraping.readGlobalString(frameId, "document.title")
                if (pageTitle.startsWith(crossword.title)) {
                    return crosshare
                } else {
                    console.info("JSON in __NEXT_DATA__ is for a different puzzle; falling back to PUZ API")
                }
            } catch (e: Exception) {
                console.info("Could not load JSON from __NEXT_DATA__; falling back to PUZ API")
            }
        }

        // Fall back to the PUZ API - this requires a separate fetch, but should always work.
        val matchResult = PUZZLE_ID_PATTERN.find(url.toString()) ?: return null
        val puzzleId = matchResult.groupValues[1]
        return AcrossLite(Http.fetchAsBinary("https://crosshare.org/api/puz/$puzzleId"))
    }
}