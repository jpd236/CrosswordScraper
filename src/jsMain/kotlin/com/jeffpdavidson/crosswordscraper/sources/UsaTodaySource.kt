package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.encodeURIComponent
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.UclickJson
import org.w3c.dom.url.URL

object UsaTodaySource : FixedHostSource() {

    private val URL_PATH_PUZZLE_ID_PATTERN =
        "/crossword/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})".toRegex()

    override val sourceName = "USA Today"
    override fun neededHostPermissions(url: URL) = listOf("https://*.usatoday.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("play.usatoday.com") && url.pathname.contains(URL_PATH_PUZZLE_ID_PATTERN)
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        // We do an unconditional HTTP fetch, so we always need permissions, even in the top-level frame.
        if (!hasPermissions(neededHostPermissions(url))) {
            return ScrapeResult.NeedPermissions(neededHostPermissions(url))
        }
        val match = URL_PATH_PUZZLE_ID_PATTERN.find(url.pathname)
        require(match != null) { "URL $url passed matchesUrl but does not match pattern" }
        val puzzleId = match.groupValues[1]
        val query = """query CrosswordsSingleGame(${'$'}id: String!) {
              __typename gameData(id: ${'$'}id) {
                __typename ...crosswordSingleGameData
              }
            }
            fragment crosswordSingleGameData on CrosswordData {
              date
              title
              author
              copyright
              width
              height
              acrossClue
              downClue
              solution
            }""".trimIndent()
        val variables = """{"id":"$puzzleId"}"""
        val data = Http.fetchAsString(
            "https://play.usatoday.com/api/query?" +
                    "operationName=CrosswordsSingleGame&" +
                    "query=${encodeURIComponent(query)}&" +
                    "variables=${encodeURIComponent(variables)}",
            listOf("x-api-type" to "games", "x-sitecode" to "USAT"),
        )
        return ScrapeResult.Success(listOf(UclickJson.fromUsaTodayJson(data)))
    }
}