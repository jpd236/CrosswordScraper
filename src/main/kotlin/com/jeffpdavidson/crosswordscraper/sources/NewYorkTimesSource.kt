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

    private val KNOWN_URL_PATTERN = "(daily|mini|bonus)/(\\d{4})/(\\d{2})/(\\d{2})".toRegex()

    override val sourceName = "New York Times"
    override val neededHostPermissions = listOf("https://*.nytimes.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("nytimes.com")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, frameId: Int): ScrapeResult {
        // Acrostic puzzles - puzzle data is embedded in gameData.
        if (url.pathname.contains("/acrostic/")) {
            val gameData = "" // Scraping.readGlobalString(frameId, "gameData")
            return if (gameData.isNotEmpty()) {
                ScrapeResult.Success(puzzles = listOf(NewYorkTimesAcrostic.fromGameData(gameData)))
            } else {
                ScrapeResult.Success(listOf())
            }
        }

        // Otherwise, assume this is a regular crossword.
        // First, try searching for embedded puzzle data in the pluribus variable.
        val pluribus = "" // Scraping.readGlobalString(frameId, "pluribus")
        if (pluribus.isNotEmpty()) {
            return getNewYorkTimesScrapeResult(NewYorkTimes.fromPluribus(pluribus, Http::fetchAsBinary))
        }

        // Otherwise, figure out the type and date so we can make a call to the NYT API.
        val (filename, stream) = getJsonFilenameAndStream(frameId, url) ?: ("" to "")
        if (filename.isNotEmpty()) {
            // For simplicity, request permissions to both the main site and the API site, even though we might only
            // need them for the main site.
            val apiUrl = "https://nyt-games-prd.appspot.com/svc/crosswords/v6/puzzle/${filename}.json"
            val neededPermissions = neededHostPermissions + getPermissionsForUrls(listOf(URL(apiUrl)))
            if (!hasPermissions(neededPermissions)) {
                return ScrapeResult.NeedPermissions(neededPermissions)
            }

            val puzzleJson = try {
                // First, try downloading the puzzle data directly from the NYT site.
                Http.fetchAsString("https://www.nytimes.com/svc/crosswords/v6/puzzle/${filename}.json")
            } catch (e: Http.HttpException) {
                // Fall back to the API site, which needs the NYT-S cookie for the current URL as a request header.
                val nytSCookie = browser.cookies.get(Details {
                    name = "NYT-S"
                    this@Details.url = url.toString()
                }).await()

                if (nytSCookie.value.isNotEmpty()) {
                    Http.fetchAsString(apiUrl, listOf("nyt-s" to nytSCookie.value))
                } else {
                    ""
                }
            }

            if (puzzleJson.isNotEmpty()) {
                return getNewYorkTimesScrapeResult(NewYorkTimes.fromApiJson(puzzleJson, stream, Http::fetchAsBinary))
            }
        }

        return ScrapeResult.Success(listOf())
    }

    private suspend fun getJsonFilenameAndStream(frameId: Int, url: URL): Pair<String, String>? {
        // First, inspect the embedded gameData variable, which may contain the data.
        val gameDataString = "" // Scraping.readGlobalJson(frameId, "gameData")
        if (gameDataString.isNotEmpty()) {
            val gameData = JSON.parse<GameDataJson>(gameDataString)
            if (gameData.filename?.isNotEmpty() == true) {
                return gameData.filename!! to (gameData.stream ?: "daily")
            }
        }

        // Next, see if the URL matches a known format; if so, we can convert that to the filename and stream.
        val urlMatcher = KNOWN_URL_PATTERN.find(url.pathname)
        if (urlMatcher != null) {
            val parts = urlMatcher.groupValues
            return "${parts[1]}/${parts[2]}-${parts[3]}-${parts[4]}" to parts[1]
        }

        return null
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