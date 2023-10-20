package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.AcrossLite
import org.w3c.dom.url.URL

object TheWeekSource : FixedHostSource() {

    override val sourceName: String = "The Week"
    override fun neededHostPermissions(url: URL) = listOf("https://*.theweek.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.host == "theweek.com"
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        // Note: parsed puzzle data is stored in PUZAPP.puz. But given that we have a direct .puz URL available, and
        // that the puzzle is read directly from that .puz URL, there's little reason to scrape from that format rather
        // than just redownloading the same .puz file.
        val scrapeFn = js("function() { return window.xrPuzUrl ? JSON.stringify(window.xrPuzUrl) : ''; }")
        val puzzleUrl = Scraping.executeFunctionForString(tabId, frameId, scrapeFn)
        if (puzzleUrl.isNotEmpty()) {
            val downloadUrl = URL(puzzleUrl.trim('"'), url.toString())
            val permissions = getPermissionsForUrls(listOf(downloadUrl))
            if (!hasPermissions(permissions)) {
                return ScrapeResult.NeedPermissions(permissions)
            }
            return ScrapeResult.Success(listOf(AcrossLite(Http.fetchAsBinary(downloadUrl.toString()))))
        }
        return ScrapeResult.Success(listOf())
    }
}
