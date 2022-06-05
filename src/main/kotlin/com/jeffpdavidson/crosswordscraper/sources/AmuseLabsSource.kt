package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.PuzzleMe
import org.w3c.dom.url.URL

object AmuseLabsSource : FixedHostSource() {

    override val sourceName = "PuzzleMe (Amuse Labs)"
    override val neededHostPermissions = listOf("https://*.amuselabs.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("amuselabs.com")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        val scrapeFn = js("function() { return window.rawc ? window.rawc : ''; }")
        val puzzleRawc = Scraping.executeFunctionForString(tabId, frameId, scrapeFn)
        if (puzzleRawc.isNotEmpty()) {
            return ScrapeResult.Success(listOf(PuzzleMe.fromRawc(puzzleRawc)))
        }
        return ScrapeResult.Success(listOf())
    }
}