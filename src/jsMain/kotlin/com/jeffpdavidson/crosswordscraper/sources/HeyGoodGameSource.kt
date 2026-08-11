package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.Ipuz
import org.w3c.dom.url.URL

object HeyGoodGameSource : FixedHostSource() {

    override val sourceName: String = "Hey, Good Game"

    private val ARCHIVE_PATH_REGEX = """^/archive/(?:easy|hard)/(\d+)""".toRegex()
    private val NUMBER_REGEX = """\d+""".toRegex()

    override fun neededHostPermissions(url: URL): List<String> =
        listOf(
            "https://*.midicrossword.com/*",
            "https://*.minicrossword.com/*",
        )

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("midicrossword.com") || url.hostIsDomainOrSubdomainOf("minicrossword.com")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        val apiPath: String
        if (url.pathname.startsWith("/unlimited")) {
            val puzzleId = scrapePuzzleId(tabId, frameId) ?: return ScrapeResult.Success(listOf())
            apiPath = "unlimited/$puzzleId"
        } else {
            val archiveMatch = ARCHIVE_PATH_REGEX.find(url.pathname)
            val dayNumber = if (archiveMatch != null) {
                archiveMatch.groupValues[1]
            } else {
                scrapePuzzleId(tabId, frameId) ?: return ScrapeResult.Success(listOf())
            }
            apiPath = "daily/${if (url.pathname.contains("/hard")) "hard" else "easy"}/$dayNumber"
        }
        val apiUrl = URL("https://play.hey.gg/api/crosswords/${url.hostname.substringBefore('.')}/$apiPath")

        val permissions = neededHostPermissions(apiUrl)
        if (!hasPermissions(permissions)) {
            return ScrapeResult.NeedPermissions(permissions)
        }

        val puzzleJson = Http.fetchAsString(apiUrl.toString())
        return ScrapeResult.Success(listOf(Ipuz(puzzleJson)))
    }

    private suspend fun scrapePuzzleId(tabId: Int, frameId: Int): Int? {
        val getTextCommand = js(
            """function(selector) {
                var elem = document.querySelector('.puzzle-label, .puzzle-id-btn');
                return (elem && elem.textContent) ? elem.textContent : '';
            }"""
        )
        val textContent = Scraping.executeFunctionForString(tabId, frameId, getTextCommand)
        val match = NUMBER_REGEX.find(textContent) ?: return null
        val number = match.value.toIntOrNull() ?: return null
        return if (number >= 0) number else null
    }
}
