package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.NewYorkTimes
import org.w3c.dom.url.URL

object NewYorkTimesSource : FixedHostSource() {

    override val sourceName = "New York Times"
    override val neededHostPermissions = listOf("https://*.nytimes.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("nytimes.com")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, frameId: Int): ScrapeResult {
        val pluribus = Scraping.readGlobalString(frameId, "pluribus")
        if (pluribus.isNotEmpty()) {
            return ScrapeResult.Success(
                puzzles = listOf(NewYorkTimes.fromPluribus(pluribus).asPuzzle()),
                puzzlesAreCrosswordLike = true,
            )
        }
        return ScrapeResult.Success(listOf())
    }
}