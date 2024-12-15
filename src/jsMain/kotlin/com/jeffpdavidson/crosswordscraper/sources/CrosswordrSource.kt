package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.Crosswordr
import org.w3c.dom.url.URL

external fun encodeURIComponent(text: String): String

object CrosswordrSource : FixedHostSource() {

    private val URL_PATH_PUZZLE_ID_PATTERN = "/puzzle/([^/]+)".toRegex()

    override val sourceName: String = "Crosswordr"
    override fun neededHostPermissions(url: URL): List<String> = listOf("https://*.crosswordr.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("crosswordr.com") && url.pathname.contains(URL_PATH_PUZZLE_ID_PATTERN)
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        // We do an unconditional HTTP fetch, so we always need permissions, even in the top-level frame.
        if (!hasPermissions(neededHostPermissions(url))) {
            return ScrapeResult.NeedPermissions(neededHostPermissions(url))
        }
        val match = URL_PATH_PUZZLE_ID_PATTERN.find(url.pathname)
        require(match != null) { "URL $url passed matchesUrl but does not match pattern" }
        val puzzleId = match.groupValues[1]
        val query = """query GetPuzzleSolveData(${'$'}puzzleId: String!, ${'$'}puzzleContext: PuzzleContext) {
              puzzleV2(puzzleId: ${'$'}puzzleId, puzzleContext: ${'$'}puzzleContext) {
                puzzle {
                  content
                  width
                  height
                  title
                  description
                  editedBy
                  byline
                  postSolveNote
                }
              }
            }""".trimIndent()
        val variables = """{"puzzleId":"$puzzleId"}"""
        // The regular applet uses POST requests, but Chrome automatically sets the origin to the Chrome Extension's URL
        // on POST requests, which causes the request to be blocked. Luckily, the equivalent GET request (with the
        // parameters moved from the POST body to query parameters) appears to work just as well.
        val data = Http.fetchAsString(
            "https://api.crosswordr.com/graphql?" +
                    "operationName=GetPuzzleSolveData&" +
                    "query=${encodeURIComponent(query)}&" +
                    "variables=${encodeURIComponent(variables)}",
            listOf("Content-Type" to "application/json"),
        )
        return ScrapeResult.Success(listOf(Crosswordr(data)))
    }
}