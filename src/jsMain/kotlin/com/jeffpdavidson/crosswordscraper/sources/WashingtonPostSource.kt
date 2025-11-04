package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.WashingtonPost
import org.w3c.dom.url.URL

object WashingtonPostSource : FixedHostSource() {

    override val sourceName = "Washington Post"
    override fun neededHostPermissions(url: URL) = listOf("https://*.wapo.pub/*")

    override fun matchesUrl(url: URL): Boolean {
        // only match iframe URL to avoid duplicate processing
        // main pages load the iframe that we will scrape
        return url.pathname.contains("/games-static/games-crossword/")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        // look through all frames to find the crossword iframe and check if it has loaded puzzle data
        val (allTabId, frames) = Scraping.getAllFrames()
        val crosswordFrame = frames.firstOrNull {
            it.url.contains("/games-static/games-crossword/")
        }

        val puzzleUrl = crosswordFrame?.let {
            // try to extract *all* puzzle URLs from the iframe's resources and detect which is displayed
            val scrapeFn = js(
                """function() {
                    // check performance API for fetch requests to the puzzle API
                    var puzzleUrls = [];
                    if (window.performance && window.performance.getEntriesByType) {
                        var entries = window.performance.getEntriesByType('resource');
                        for (var i = 0; i < entries.length; i++) {
                            var name = entries[i].name;
                            if (name && name.includes('games-service-prod.site.aws.wapo.pub/crossword/levels/')) {
                                puzzleUrls.push(name);
                            }
                        }
                    }

                    // check if page content contains "Evan Birnholz" // TODO this feels hacky...
                    var isSundayPuzzle = document.body && document.body.innerText.includes('Evan Birnholz');

                    return JSON.stringify({
                        urls: puzzleUrls,
                        isSunday: isSundayPuzzle
                    });
                }"""
            )

            val resultJson = Scraping.executeFunctionForString(allTabId, it.frameId, scrapeFn)
            if (resultJson.isNotEmpty() && resultJson != "{}") {
                val result = JSON.parse<dynamic>(resultJson)
                val urlStrings = result.urls as Array<String>
                val isSundayPuzzle = result.isSunday as Boolean

                urlStrings.firstOrNull { urlString ->
                    urlString.contains(if (isSundayPuzzle) "/sunday/" else "/daily/")
                } ?: urlStrings.firstOrNull()
            } else {
                null
            }
        } ?: return ScrapeResult.Success()

        val neededPermissions = getPermissionsForUrls(listOf(URL(puzzleUrl)))
        if (!hasPermissions(neededPermissions)) {
            return ScrapeResult.NeedPermissions(neededPermissions)
        }

        return try {
            ScrapeResult.Success(listOf(WashingtonPost(Http.fetchAsString(puzzleUrl))))
        } catch (e: Http.HttpException) {
            // TODO? avoid swallowing exception
            ScrapeResult.Success()
        }
    }
}