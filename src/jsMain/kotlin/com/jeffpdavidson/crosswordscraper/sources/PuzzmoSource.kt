package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.Xd
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.url.URL

object PuzzmoSource : FixedHostSource() {

    private val json = Json { ignoreUnknownKeys = true }

    private val PUZZLE_PATH_PATTERN = Regex("^/puzzle/(\\d{4}-\\d{2}-\\d{2})/.+")

    override val sourceName: String = "Puzzmo"

    override fun neededHostPermissions(url: URL): List<String> = listOf("https://*.puzzmo.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("puzzmo.com") && PUZZLE_PATH_PATTERN.matches(url.pathname)
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        // Execute the fetch natively in the page's context in order to make POST requests with the right origin.
        val jsonFetchFn = js(
            $$"""function() {
              var pathMatch = window.location.pathname.match(/^\/puzzle\/(.+)/);
              if (!pathMatch) return Promise.resolve("{}");
              var finderKey = "today:/" + pathMatch[1];

              return fetch("https://www.puzzmo.com/_api/prod/graphql?PlayGameScreenQuery", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  operationName: "PlayGameScreenQuery",
                  query: "query PlayGameScreenQuery($finderKey: String!, $gameContext: StartGameContext!) { " +
                      "startOrFindGameplay(finderKey: $finderKey, context: $gameContext) { " +
                          "... on HasGamePlayed { gamePlayed { puzzle { puzzle } } } " +
                      "} " +
                  "}",
                  variables: {
                    finderKey: finderKey,
                    gameContext: { partnerSlug: null, pingOwnerForMultiplayer: true }
                  }
                })
              }).then(function(response) {
                return response.text();
              });
            }"""
        )
        return scrapePuzzmoData<GraphQLResponse>(tabId, frameId, jsonFetchFn) {
            it.data?.startOrFindGameplay?.gamePlayed
        }
    }

    internal suspend inline fun <reified ResponseType> scrapePuzzmoData(
        tabId: Int,
        frameId: Int,
        jsonFetchFn: dynamic,
        gameplayExtractFn: (ResponseType) -> Gameplay?,
    ): ScrapeResult {
        val responseJson = Scraping.executeFunctionForString(tabId, frameId, jsonFetchFn)
        val response = json.decodeFromString<ResponseType>(responseJson)
        val gameplay = gameplayExtractFn(response)
        val puzzleData = gameplay?.puzzle?.puzzle ?: return ScrapeResult.Success(listOf())
        return ScrapeResult.Success(listOf(Xd(puzzleData)))
    }

    @Serializable
    internal data class Puzzle(
        val puzzle: String? = null
    )

    @Serializable
    internal data class Gameplay(
        val puzzle: Puzzle? = null
    )

    @Serializable
    private data class StartOrFindGameplay(
        val gamePlayed: Gameplay? = null
    )

    @Serializable
    private data class Data(
        val startOrFindGameplay: StartOrFindGameplay? = null
    )

    @Serializable
    private data class GraphQLResponse(
        val data: Data? = null
    )
}
