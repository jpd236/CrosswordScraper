package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.Cnn
import org.w3c.dom.url.URL

object CnnSource : FixedHostSource() {

    override val sourceName: String = "CNN"
    override fun neededHostPermissions(url: URL): List<String> = listOf("https://*.gamedistribution.com/*")
    override fun matchesUrl(url: URL): Boolean = url.hostIsDomainOrSubdomainOf("sg.gamedistribution.com")

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        // Read the currently highlighted clue from the applet. Then iterate over local stored state for each puzzle.
        // Look for a match where the saved currently-highlighted clue equals the one we pulled from the applet.
        val puzzleId = Scraping.executeFunctionForString(
            tabId, frameId, js(
                """function() {
                    var currentClueBar = document.getElementsByClassName("currentClueBar");
                    if (!currentClueBar || currentClueBar.length == 0) {
                        return '';
                    }
                    var puzzleStateKeyPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
                    var currentClue = currentClueBar[0].innerText;
                    for (var i = 0; i < localStorage.length; i++) {
                        var storageKey = localStorage.key(i);
                        if (!puzzleStateKeyPattern.test(storageKey)) {
                            continue;
                        }
                        var currentClueId = JSON.parse(localStorage.getItem(storageKey)).currentClueId;
                        if (!currentClueId) {
                            continue;
                        }
                        if (currentClue.includes('|\u00a0' + currentClueId + ' (')) {
                            return storageKey;
                        }
                    }
                    return '';
                }"""
            )
        )
        if (puzzleId.isEmpty()) {
            return ScrapeResult.Success(listOf())
        }
        if (!hasPermissions(neededHostPermissions(url))) {
            return ScrapeResult.NeedPermissions(neededHostPermissions(url))
        }
        val puzzleUrl = "https://crosswords-sgweb.gamedistribution.com/storage/cnn_demo-crossword/$puzzleId.json"
        val data = Http.fetchAsString(puzzleUrl)
        return ScrapeResult.Success(listOf(Cnn(data)))
    }
}