package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.kotwords.formats.DailyPrincetonian
import org.w3c.dom.url.URL

object DailyPrincetonianSource : FixedHostSource() {

    override val sourceName: String = "The Daily Princetonian"
    override fun neededHostPermissions(url: URL) = listOf("https://*.crossword.dailyprincetonian.com//*")

    private const val API_URL = "https://crossword.dailyprincetonian.com/api/crosswords"

    override fun matchesUrl(url: URL): Boolean {
        return url.host == "crossword.dailyprincetonian.com"
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        if (!url.pathname.matches("/[0-9a-z-]+".toRegex())) {
            return ScrapeResult.Success()
        }
        val crosswordId = url.pathname.substring(1)
        val neededPermissions = neededHostPermissions(url)
        if (!hasPermissions(neededPermissions)) {
            return ScrapeResult.NeedPermissions(neededPermissions)
        }
        return ScrapeResult.Success(
            listOf(
                DailyPrincetonian(
                    crosswordJson = Http.fetchAsString("$API_URL/$crosswordId"),
                    authorsJson = Http.fetchAsString("$API_URL/$crosswordId/authors"),
                    cluesJson = Http.fetchAsString("$API_URL/$crosswordId/clues")
                )
            )
        )
    }
}