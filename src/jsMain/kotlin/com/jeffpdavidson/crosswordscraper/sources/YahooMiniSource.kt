package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.Xd
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.w3c.dom.url.URL

object YahooMiniSource : FixedHostSource() {

    override val sourceName: String = "Yahoo Mini"

    @Suppress("RegExpRedundantEscape") // https://youtrack.jetbrains.com/issue/KTIJ-33836
    private val NEXT_F_PATTERN = """self\.__next_f\.push\(\[\d+,\s*("(?:\\.|[^"\\])*")\]\)""".toRegex()

    override fun neededHostPermissions(url: URL): List<String> = listOf("https://*.yahoo.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("yahoo.com")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        val scrapeFn = js(
            """function() {
                return Array.from(window.document.scripts)
                    .map(function(elem) { return elem.textContent; })
                    .join('\n');
            }"""
        )
        val allScriptContents = Scraping.executeFunctionForString(tabId, frameId, scrapeFn)

        for (match in NEXT_F_PATTERN.findAll(allScriptContents)) {
            val innerString = try {
                Json.decodeFromString<String>(match.groupValues[1])
            } catch (_: Exception) {
                continue
            }

            val bracketIndex = innerString.indexOf('[')
            if (bracketIndex == -1) {
                continue
            }
            val innerJsonStr = innerString.substring(bracketIndex)
            val puzzleData = try {
                val innerJson = Json.parseToJsonElement(innerJsonStr)
                findPuzzle(innerJson)
            } catch (_: Exception) {
                null
            } ?: continue

            return ScrapeResult.Success(listOf(Xd(puzzleData)))
        }

        return ScrapeResult.Success(listOf())
    }

    /** Recurse through the given JSON to find a string that looks like Xd-format data. */
    private fun findPuzzle(element: JsonElement): String? {
        if (element is JsonPrimitive && element.isString && element.content.contains("## Metadata")) {
            return element.content
        }
        if (element is JsonObject) {
            for (value in element.values) {
                val res = findPuzzle(value)
                if (res != null) return res
            }
        }
        if (element is JsonArray) {
            for (item in element) {
                val res = findPuzzle(item)
                if (res != null) return res
            }
        }
        return null
    }
}
