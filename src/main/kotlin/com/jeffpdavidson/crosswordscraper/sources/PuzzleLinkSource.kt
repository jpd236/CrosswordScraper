package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.AcrossLite
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.w3c.dom.url.URL

object PuzzleLinkSource : Source {

    override val sourceName: String = "Puzzle Link"

    override fun matchesUrl(url: URL): Boolean = url.protocol == "http:" || url.protocol == "https:"

    override suspend fun scrapePuzzles(url: URL, frameId: Int, isTopLevel: Boolean): ScrapeResult {
        // We can't access the frame contents of internal frames without requesting permissions up front, so just assume
        // there are no iframes with .puz links inside.
        if (!isTopLevel) {
            return ScrapeResult.Success(listOf())
        }

        // Find all .puz links on the page.
        val puzzleUrlsJson = Scraping.executeScriptForString(frameId, "puzzlelink")
        val puzzleUrls = Json.decodeFromString(ListSerializer(String.serializer()), puzzleUrlsJson)
            .distinct()
            .map { URL(it) }
            .filter { setOf("http:", "https:").contains(it.protocol) }

        // Determine and check all the permissions we'll need to download these puzzles.
        val neededPermissions = getPermissionsForUrls(puzzleUrls)
        if (!hasPermissions(neededPermissions)) {
            return ScrapeResult.NeedPermissions(neededPermissions)
        }

        return ScrapeResult.Success(puzzleUrls.map { AcrossLite(Http.fetchAsBinary(it.toString())) })
    }
}