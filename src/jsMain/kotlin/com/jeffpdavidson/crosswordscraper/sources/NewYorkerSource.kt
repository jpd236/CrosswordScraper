package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.Xd
import org.w3c.dom.url.URL

object NewYorkerSource : FixedHostSource() {

    override val sourceName: String = "New Yorker"
    override fun neededHostPermissions(url: URL) = listOf("https://*.newyorker.com/*")

    override fun matchesUrl(url: URL): Boolean = url.hostIsDomainOrSubdomainOf("newyorker.com")
    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        val idScrapeFn = js(
            """function() {
                var crosswordElems = document.getElementsByClassName('crossword-container');
                if (crosswordElems.length == 0) {
                  return '';
                }
                return crosswordElems[0].id;
            }"""
        )
        val id = Scraping.executeFunctionForString(tabId, frameId, idScrapeFn)
        if (id.isEmpty()) {
            return ScrapeResult.Success(listOf())
        }
        val puzzleUrl = "https://puzzles-games-api.gp-prod.conde.digital/api/v1/games/$id"
        val permissions = getPermissionsForUrls(listOf(URL(puzzleUrl)))
        if (!hasPermissions(permissions)) {
            return ScrapeResult.NeedPermissions(permissions)
        }
        val puzzleJson = JSON.parse<NewYorkerJson>(Http.fetchAsString(puzzleUrl))
        return ScrapeResult.Success(listOf(Xd(puzzleJson.data)))
    }
}

private external interface NewYorkerJson {
    val data: String
}