package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.PuzzmoSource.scrapePuzzmoData
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
        return url.hostIsDomainOrSubdomainOf("yahoo.com") && url.pathname.contains("/play/mini-crossword")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        if (url.hostIsDomainOrSubdomainOf("games.yahoo.com")) {
            return scrapeGraphQL(tabId, frameId)
        }
        // TODO: See if we can remove this once the new interface is loading consistently.
        return scrapeLegacy(tabId, frameId)
    }

    private suspend fun scrapeGraphQL(tabId: Int, frameId: Int): ScrapeResult {
        val jsonFetchFn = js(
            """function() {
              /* Find the date of the puzzle being viewed in the header. */
              var headerElem = document.querySelector('header');
              var dateMatch =
                    (headerElem && headerElem.textContent) ?
                            headerElem.textContent.match(/[A-Z][a-z]+ \d{1,2}, \d{4}/) : null;
              if (!dateMatch) {
                return Promise.resolve("{}");
              }
              var date = new Date(dateMatch[0] + ' UTC');
              if (isNaN(date.getTime())) {
                return Promise.resolve("{}");
              }
              var dateKey = date.toISOString().split('T')[0];

              /* Fetch the query ID by searching for it in all of the script chunks. */
              var scripts = Array.from(document.querySelectorAll('script[src]'))
                .map(function(s) { return s.src; })
                .filter(function(src) { return src.includes('/chunks/'); });
              return Promise.all(scripts.map(function(src) {
                return fetch(src, { cache: 'force-cache' }).then(function(res) {
                  return res.text();
                }).then(function(scriptText) {
                  var m = scriptText.match(/id:"([a-f0-9]{64})"[^;]{1,100}name:"StartOrFindGameplayQuery"/) ||
                          scriptText.match(/name:"StartOrFindGameplayQuery"[^;]{1,100}id:"([a-f0-9]{64})"/);
                  return m ? m[1] : null;
                }).catch(function() { return null; });
              })).then(function(results) {
                var queryId = results.find(function(id) { return !!id; });
                if (!queryId) {
                  return "{}";
                }

                return fetch('https://playground-api.games.yahoo.com/graphql?StartOrFindGameplayQuery', {
                  method: 'POST',
                  headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json; charset=utf-8'
                  },
                  credentials: 'include',
                  body: JSON.stringify({
                    id: queryId,
                    operationName: 'StartOrFindGameplayQuery',
                    variables: {
                      finderKey: 'today:/' + dateKey + '/mini-crossword',
                      context: {},
                      viewerID: '-'
                    }
                  })
                }).then(function(response) {
                  return response.text();
                });
              });
            }"""
        )
        return scrapePuzzmoData(tabId, frameId, jsonFetchFn)
    }

    private suspend fun scrapeLegacy(tabId: Int, frameId: Int): ScrapeResult {
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

            for (line in innerString.lines()) {
                val innerJson = line.substringAfter(':', "").trim()
                if (innerJson.isEmpty() || (!innerJson.startsWith("{") && !innerJson.startsWith("["))) {
                    continue
                }

                val puzzleData = try {
                    val jsonElement = Json.parseToJsonElement(innerJson)
                    findPuzzle(jsonElement)
                } catch (_: Exception) {
                    null
                } ?: continue

                return ScrapeResult.Success(listOf(Xd(puzzleData)))
            }
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
