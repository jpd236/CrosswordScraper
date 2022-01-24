package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.NewYorkTimes
import com.jeffpdavidson.kotwords.formats.NewYorkTimesAcrostic
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
            val nyt = NewYorkTimes.fromPluribus(pluribus, Http::fetchAsBinary)
            if (nyt.getExtraDataUrls().isNotEmpty()) {
                val extraDataPermissions = getPermissionsForUrls(nyt.getExtraDataUrls().map { URL(it) })
                if (!hasPermissions(extraDataPermissions)) {
                    return ScrapeResult.NeedPermissions(
                        extraDataPermissions,
                        prompt = "Grant permission (needed to scrape grid graphics)"
                    )
                }
            }
            return ScrapeResult.Success(
                puzzles = listOf(nyt),
            )
        }
        val gameData = Scraping.readGlobalString(frameId, "gameData")
        if (gameData.isNotEmpty()) {
            return ScrapeResult.Success(
                puzzles = listOf(NewYorkTimesAcrostic.fromGameData(gameData)),
            )
        }
        return ScrapeResult.Success(listOf())
    }
}