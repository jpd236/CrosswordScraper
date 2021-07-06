package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.UclickJson
import org.w3c.dom.url.URL

object UniversalSource : FixedHostSource() {

    override val sourceName: String = "Universal Uclick"
    override val neededHostPermissions = listOf("https://*.universaluclick.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.host == "embed.universaluclick.com"
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, frameId: Int): ScrapeResult {
        val puzzleJson = Scraping.readGlobalJson(frameId, "crossword.jsonData")
        if (puzzleJson.isNotEmpty()) {
            return ScrapeResult.Success(listOf(UclickJson(puzzleJson)))
        }
        return ScrapeResult.Success(listOf())
    }
}