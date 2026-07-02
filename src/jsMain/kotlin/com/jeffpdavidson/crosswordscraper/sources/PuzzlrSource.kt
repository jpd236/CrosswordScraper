package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.encodeURIComponent
import com.jeffpdavidson.kotwords.formats.Puzzlr
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.url.URL


object PuzzlrSource : Source {

    override val sourceName: String = "Puzzlr"

    override fun matchesUrl(url: URL): Boolean = url.protocol == "http:" || url.protocol == "https:"

    @Serializable
    private data class PuzzlrTag(
        val tenant: String,
        val level: String,
    )

    override suspend fun scrapePuzzles(url: URL, tabId: Int, frameId: Int, isTopLevel: Boolean): ScrapeResult {
        // We can't access the frame contents of internal frames without requesting permissions up front, so just assume
        // there are no iframes with Puzzlr applets inside.
        if (!isTopLevel) {
            return ScrapeResult.Success(listOf())
        }

        val getPuzzlrTagsCommand = js(
            """function() {
                return JSON.stringify(
                    Array.from(
                        window.document.querySelectorAll('puzzlr-crossword')
                    ).map(function(elem) {
                        return {
                            tenant: elem.getAttribute('data-tenant'),
                            level: elem.getAttribute('data-level')
                        };
                    }).filter(function(item) {
                        return item.tenant && item.level;
                    })
                );
            }"""
        )

        val puzzlrTagsJson = Scraping.executeFunctionForString(tabId, frameId, getPuzzlrTagsCommand)
        if (puzzlrTagsJson.isEmpty()) {
            return ScrapeResult.Success(listOf())
        }
        val tags = Json.decodeFromString<List<PuzzlrTag>>(puzzlrTagsJson)
        if (tags.isEmpty()) {
            return ScrapeResult.Success(listOf())
        }

        val neededPermissions = listOf("https://api.puzzlr.net/*")
        if (!hasPermissions(neededPermissions)) {
            return ScrapeResult.NeedPermissions(neededPermissions)
        }

        val puzzles = tags.map { tag ->
            val encodedInput = encodeURIComponent("""{"0":{"tenant":"${tag.tenant}","shortId":"${tag.level}"}}""")
            val apiUrl = "https://api.puzzlr.net/trpc/crossword.getLevel?batch=1&input=$encodedInput"
            val response = Http.fetchAsString(apiUrl)
            Puzzlr(response)
        }

        return ScrapeResult.Success(puzzles)
    }
}
