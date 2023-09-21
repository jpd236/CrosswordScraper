package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.WallStreetJournal
import com.jeffpdavidson.kotwords.formats.WallStreetJournalAcrostic
import org.w3c.dom.url.URL

object WallStreetJournalSource : FixedHostSource() {

    override val sourceName = "Wall Street Journal"
    override fun neededHostPermissions(url: URL) = listOf("https://*.wsj.com/*", "https://s3.amazonaws.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return (url.hostIsDomainOrSubdomainOf("wsj.com") || url.host == "s3.amazonaws.com")
                && url.pathname.contains("/puzzles/crossword/")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        val scrapeFn = js(
            """function() {
                return window.oApp.puzzle.JSON ? JSON.stringify(window.oApp.puzzle.JSON) : '';
            }"""
        )
        val puzzleJsonString = Scraping.executeFunctionForString(tabId, frameId, scrapeFn)
        if (puzzleJsonString.isEmpty()) {
            return ScrapeResult.Success()
        }
        val puzzleJson = JSON.parse<PuzzleJson>(puzzleJsonString)
        if (puzzleJson.data.meta.type == "acrostic") {
            return ScrapeResult.Success(puzzles = listOf(WallStreetJournalAcrostic(JSON.stringify(puzzleJson.data))))
        }
        return ScrapeResult.Success(listOf(WallStreetJournal(puzzleJsonString)))
    }
}

private external interface Meta {
    val type: String?
}

private external interface Data {
    val meta: Meta
}

private external interface PuzzleJson {
    val data: Data
}
