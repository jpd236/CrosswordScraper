package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.Crosswordable
import com.jeffpdavidson.kotwords.formats.WallStreetJournal
import org.w3c.dom.url.URL

object WallStreetJournalSource : Source {

    override val sourceName = "Wall Street Journal"
    override val neededHostPermissions = listOf("https://*.wsj.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return (url.host == "wsj.com" || url.host.endsWith(".wsj.com"))
                && url.pathname.startsWith("/puzzles/crossword/")
    }

    override suspend fun scrapePuzzle(url: URL, frameId: Int): Crosswordable? {
        val puzzleJson = Scraping.readGlobalJson(frameId, "oApp.puzzle.JSON")
        if (puzzleJson.isNotEmpty()) {
            return WallStreetJournal(puzzleJson)
        }
        return null
    }
}