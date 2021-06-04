package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.Crosswordable
import com.jeffpdavidson.kotwords.formats.PuzzleMe
import org.w3c.dom.url.URL

object AmuseLabsSource : Source {

    override val sourceName = "PuzzleMe (Amuse Labs)"
    override val neededHostPermissions = listOf("https://*.amuselabs.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("amuselabs.com")
    }

    override suspend fun scrapePuzzle(url: URL, frameId: Int): Crosswordable? {
        val puzzleRawc = Scraping.readGlobalString(frameId, "rawc")
        if (puzzleRawc.isNotEmpty()) {
            return PuzzleMe.fromRawc(puzzleRawc)
        }
        return null
    }
}