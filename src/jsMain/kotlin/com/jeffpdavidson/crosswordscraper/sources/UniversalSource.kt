package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.UclickJson
import org.w3c.dom.url.URL

object UniversalSource : FixedHostSource() {

    override val sourceName: String = "Universal Uclick"

    private val hostToNeededPermissionMap = mapOf(
        "embed.universaluclick.com" to "https://*.universaluclick.com/*",
        "securegames.iwin.com" to "https://*.iwin.com/*",
    )

    override fun neededHostPermissions(url: URL): List<String> =
        listOf(hostToNeededPermissionMap[url.host] ?: throw UnsupportedOperationException("Unknown URL: $url"))

    override fun matchesUrl(url: URL): Boolean = url.host in hostToNeededPermissionMap.keys

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        val scrapeFn = js(
            """function() {
                return window.crossword && window.crossword.jsonData ? JSON.stringify(window.crossword.jsonData) : '';
            }"""
        )
        val puzzleJson = Scraping.executeFunctionForString(tabId, frameId, scrapeFn)
        if (puzzleJson.isNotEmpty()) {
            return ScrapeResult.Success(listOf(UclickJson(puzzleJson)))
        }
        return ScrapeResult.Success(listOf())
    }
}