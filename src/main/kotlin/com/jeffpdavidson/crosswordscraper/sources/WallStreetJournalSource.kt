package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.WallStreetJournal
import com.jeffpdavidson.kotwords.formats.WallStreetJournalAcrostic
import org.w3c.dom.url.URL

object WallStreetJournalSource : FixedHostSource() {

    override val sourceName = "Wall Street Journal"
    override val neededHostPermissions = listOf("https://*.wsj.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return (url.host == "wsj.com" || url.host.endsWith(".wsj.com"))
                && url.pathname.startsWith("/puzzles/crossword/")
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
            return ScrapeResult.Success(
                puzzles = listOf(WallStreetJournalAcrostic(JSON.stringify(puzzleJson.data)).asAcrostic())
            )
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
