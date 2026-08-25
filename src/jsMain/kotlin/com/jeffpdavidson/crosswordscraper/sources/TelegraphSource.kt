package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.Telegraph
import org.w3c.dom.url.URL

object TelegraphSource : FixedHostSource() {

    override val sourceName: String = "Telegraph"
    override fun neededHostPermissions(url: URL): List<String> = listOf("https://*.telegraph.co.uk/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("telegraph.co.uk") &&
                url.pathname.removeSuffix("/") == "/puzzles/gameplay/crossword"
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        val id = url.searchParams.get("id")
        val variant = url.searchParams.get("variant")
        if (id.isNullOrEmpty() || variant.isNullOrEmpty()) {
            return ScrapeResult.Success()
        }
        val dataUrl = "https://puzzlesdata.telegraph.co.uk/puzzles/$variant/$id.json"
        val permissions = getPermissionsForUrls(listOf(URL(dataUrl)))
        if (!hasPermissions(permissions)) {
            return ScrapeResult.NeedPermissions(permissions)
        }
        val puzzleJson = Http.fetchAsString(dataUrl)
        return ScrapeResult.Success(listOf(Telegraph(puzzleJson)))
    }
}
