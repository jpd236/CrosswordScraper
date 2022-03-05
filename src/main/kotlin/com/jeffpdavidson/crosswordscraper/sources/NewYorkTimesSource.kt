package com.jeffpdavidson.crosswordscraper.sources

import browser.cookies.Details
import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.NewYorkTimes
import com.jeffpdavidson.kotwords.formats.NewYorkTimesAcrostic
import kotlinx.coroutines.await
import org.w3c.dom.url.URL

object NewYorkTimesSource : FixedHostSource() {

    override val sourceName = "New York Times"
    override val neededHostPermissions = listOf("https://*.nytimes.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("nytimes.com")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, frameId: Int): ScrapeResult {
        // Acrostic puzzles - puzzle data is embedded in gameData.
        if (url.pathname.contains("/acrostic/")) {
            val gameData = Scraping.readGlobalString(frameId, "gameData")
            return if (gameData.isNotEmpty()) {
                ScrapeResult.Success(puzzles = listOf(NewYorkTimesAcrostic.fromGameData(gameData)))
            } else {
                ScrapeResult.Success(listOf())
            }
        }

        // Otherwise, assume this is a regular crossword.
        // First, try searching for embedded puzzle data in the pluribus variable.
        val pluribus = Scraping.readGlobalString(frameId, "pluribus")
        if (pluribus.isNotEmpty()) {
            return getNewYorkTimesScrapeResult(NewYorkTimes.fromPluribus(pluribus, Http::fetchAsBinary))
        }

        // Next, inspect the embedded gameData variable, which may contain the filename to use for another API call to
        // obtain the puzzle data.
        val gameDataString = Scraping.readGlobalJson(frameId, "gameData")
        if (gameDataString.isNotEmpty()) {
            val gameData = JSON.parse<GameDataJson>(gameDataString)
            if (gameData.filename?.isNotEmpty() == true) {
                val puzzleJsonUrl =
                    "https://nyt-games-prd.appspot.com/svc/crosswords/v6/puzzle/${gameData.filename}.json"

                // Need permission to access NYT cookies and API.
                val neededPermissions = neededHostPermissions + getPermissionsForUrls(listOf(URL(puzzleJsonUrl)))
                if (!hasPermissions(neededPermissions)) {
                    return ScrapeResult.NeedPermissions(neededPermissions)
                }

                // Obtain the NYT-S cookie for the current URL so we can use it in the API request.
                val nytSCookie = browser.cookies.get(Details {
                    name = "NYT-S"
                    this@Details.url = url.toString()
                }).await()
                if (nytSCookie.value.isNotEmpty()) {
                    val puzzleJson = Http.fetchAsString(puzzleJsonUrl, listOf("nyt-s" to nytSCookie.value))
                    return getNewYorkTimesScrapeResult(
                        NewYorkTimes.fromApiJson(puzzleJson, gameData.stream ?: "daily", Http::fetchAsBinary)
                    )
                }
            }
        }

        return ScrapeResult.Success(listOf())
    }

    private suspend fun getNewYorkTimesScrapeResult(nyt: NewYorkTimes): ScrapeResult {
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
}

private external interface GameDataJson {
    val filename: String?
    val stream: String?
}