package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.Guardian
import org.w3c.dom.url.URL

object GuardianSource : FixedHostSource() {

    override val sourceName: String = "The Guardian"
    override fun neededHostPermissions(url: URL) = listOf("https://*.theguardian.com/*")

    override fun matchesUrl(url: URL): Boolean = url.hostIsDomainOrSubdomainOf("theguardian.com")
    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        val scrapeFn = js(
            """function() {
                var crosswordElems = document.getElementsByClassName("js-crossword");
                if (crosswordElems.length == 0 || !crosswordElems[0].dataset["crosswordData"]) {
                  return '';
                }
                return crosswordElems[0].dataset["crosswordData"];
            }"""
        )
        val puzzleJson = Scraping.executeFunctionForString(tabId, frameId, scrapeFn)
        if (puzzleJson.isNotEmpty()) {
            return ScrapeResult.Success(listOf(Guardian(puzzleJson)))
        }
        return ScrapeResult.Success(listOf())
    }
}