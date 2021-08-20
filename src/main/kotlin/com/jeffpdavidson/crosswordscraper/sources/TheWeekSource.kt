package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.AcrossLite
import org.w3c.dom.url.URL

object TheWeekSource : FixedHostSource() {

    override val sourceName: String = "The Week"
    override val neededHostPermissions = listOf("https://*.theweek.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.host == "theweek.com"
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, frameId: Int): ScrapeResult {
        // Note: parsed puzzle data is stored in PUZAPP.puz. But given that we have a direct .puz URL available, and
        // that the puzzle is read directly from that .puz URL, there's little reason to scrape from that format rather
        // than just redownloading the same .puz file.
        val puzzleUrl = Scraping.readGlobalJson(frameId, "xrPuzUrl")
        if (puzzleUrl.isNotEmpty()) {
            if (!hasPermissions(neededHostPermissions)) {
                return ScrapeResult.NeedPermissions(neededHostPermissions)
            }
            return ScrapeResult.Success(listOf(AcrossLite(Http.fetchAsBinary("${url.origin}${puzzleUrl.trim('"')}"))))
        }
        return ScrapeResult.Success(listOf())
    }
}
