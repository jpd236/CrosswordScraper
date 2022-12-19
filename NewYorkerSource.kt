package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.PuzzleMe
import org.w3c.dom.url.URL

object NewYorkerSource : FixedHostSource() {

    override val sourceName = "NewYorker"
    override val neededHostPermissions = listOf("https://*.newyorker.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("newyorker.com")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        val scrapeFn = js("function() { return window.rawc ? window.rawc : ''; }")
        val puzzleRawc = Scraping.executeFunctionForString(tabId, frameId, scrapeFn)
        if (puzzleRawc.isNotEmpty()) {
            val onReadyScrapeFn = js("function() { return window.onReady ? window.onReady.toString() : ''; }")
            val onReadyFn = Scraping.executeFunctionForString(tabId, frameId, onReadyScrapeFn)
            return ScrapeResult.Success(listOf(PuzzleMe.fromRawc(puzzleRawc, onReadyFn)))
        }
        return ScrapeResult.Success(listOf())
    }
}
