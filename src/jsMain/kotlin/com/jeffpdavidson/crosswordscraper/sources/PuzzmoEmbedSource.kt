package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.sources.PuzzmoSource.scrapePuzzmoData
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import kotlinx.serialization.Serializable
import org.w3c.dom.url.URL

object PuzzmoEmbedSource : FixedHostSource() {

    override val sourceName: String = "Puzzmo Embed"

    override fun neededHostPermissions(url: URL): List<String> = listOf("https://*.puzzmo.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("puzzmo.com") && url.pathname.startsWith("/_embed/")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        // Execute the fetch natively in the page's context in order to make POST requests with the right origin.
        val jsonFetchFn = js(
            $$"""function() {
              var searchParams = new URLSearchParams(window.location.search);
              var embedId = searchParams.get("embedID");
              var submissionId = searchParams.get("submissionID");
              var dateKey = searchParams.get("dateKey");
              var hasPuzzleKey = submissionId || dateKey;

              if (window.location.pathname.endsWith("/latest.html") && embedId && !hasPuzzleKey) {
                return fetch("https://puzmo.blob.core.windows.net/embed-cache/" + embedId + ".json")
                  .then(function(response) { return response.text(); });
              }

              if (!embedId || !hasPuzzleKey) return Promise.resolve("{}");

              var fetchOptions = {
                embedID: embedId,
              };
              if (submissionId) {
                fetchOptions.submissionID = submissionId;
              } else if (dateKey) {
                fetchOptions.dateKey = dateKey;
              }

              return fetch("https://api.puzzmo.com/graphql?op=embedConfigBootstrapQueryMutation", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  query: "mutation embedConfigBootstrapQueryMutation($id: ID!, $options: JSON) { " +
                      "createGameplayForEmbedConfig(id: $id, options: $options) { " +
                          "gameplay { puzzle { puzzle } } " +
                      "} " +
                  "}",
                  variables: {
                    id: embedId,
                    options: fetchOptions,
                  }
                })
              }).then(function(response) {
                return response.text();
              });
            }"""
        )
        return scrapePuzzmoData<GraphQLResponse>(tabId, frameId, jsonFetchFn) {
            it.data?.createGameplayForEmbedConfig?.gameplay
        }
    }

    @Serializable
    private data class CreateGameplayForEmbedConfig(
        val gameplay: PuzzmoSource.Gameplay? = null
    )

    @Serializable
    private data class Data(
        val createGameplayForEmbedConfig: CreateGameplayForEmbedConfig? = null
    )

    @Serializable
    private data class GraphQLResponse(
        val data: Data? = null
    )
}
