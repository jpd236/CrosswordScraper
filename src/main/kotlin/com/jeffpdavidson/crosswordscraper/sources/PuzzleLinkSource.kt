package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.AcrossLite
import com.jeffpdavidson.kotwords.formats.Ipuz
import com.jeffpdavidson.kotwords.formats.JpzFile
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.w3c.dom.url.URL

object PuzzleLinkSource : Source {

    override val sourceName: String = "Puzzle Link"

    override fun matchesUrl(url: URL): Boolean = url.protocol == "http:" || url.protocol == "https:"

    override suspend fun scrapePuzzles(url: URL, tabId: Int, frameId: Int, isTopLevel: Boolean): ScrapeResult {
        // We can't access the frame contents of internal frames without requesting permissions up front, so just assume
        // there are no iframes with .puz links inside.
        if (!isTopLevel) {
            return ScrapeResult.Success(listOf())
        }

        // Find all potential puzzle links on the page. Note that the selector may return invalid links like
        // http://puzzle.page/viewer.html?puzzle=my_puzzle.puz, so we filter these out later when we can parse the URL.
        val getUrlsCommand = js(
            """function() {
                return JSON.stringify(
                    Array.from(
                        window.document.querySelectorAll('a:is([href$=".puz"],[href$=".jpz"],[href$=".ipuz"])').values()
                    ).map(function(elem) { return elem.href; })
                );
            }"""
        )
        val puzzleUrlsJson = Scraping.executeFunctionForString(tabId, frameId, getUrlsCommand)
        val puzzleUrls = Json.decodeFromString(ListSerializer(String.serializer()), puzzleUrlsJson)
            .distinct()
            .map { URL(it) }
            .filter { setOf("http:", "https:").contains(it.protocol) }
            .filter { it.pathname.contains(".(puz|jpz|ipuz)$".toRegex()) }

        // Determine and check all the permissions we'll need to download these puzzles.
        val neededPermissions = getPermissionsForUrls(puzzleUrls)
        if (!hasPermissions(neededPermissions)) {
            return ScrapeResult.NeedPermissions(neededPermissions)
        }

        return ScrapeResult.Success(
            puzzleUrls.map {
                val urlString = it.toString()
                when (urlString.substringAfterLast('.')) {
                    "jpz" -> JpzFile(Http.fetchAsBinary(urlString))
                    "ipuz" -> Ipuz(Http.fetchAsString(urlString))
                    else -> AcrossLite(Http.fetchAsBinary(urlString))
                }
            }
        )
    }
}