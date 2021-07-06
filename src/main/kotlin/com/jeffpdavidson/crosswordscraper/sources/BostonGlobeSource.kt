package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.BostonGlobe
import org.w3c.dom.url.URL

object BostonGlobeSource : FixedHostSource() {

    override val sourceName = "Boston Globe"
    override val neededHostPermissions = listOf("https://*.bostonglobe.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("bostonglobe.com") && url.pathname.startsWith("/games-comics/crossword")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, frameId: Int): ScrapeResult {
        val html = Scraping.readGlobalString(frameId, "document.body.outerHTML")
        return ScrapeResult.Success(listOf(BostonGlobe(html)))
    }
}