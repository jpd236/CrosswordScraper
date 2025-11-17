package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.Ipuz
import org.w3c.dom.url.URL

object CrosswordNexusSource : FixedHostSource() {
    override val sourceName: String = "Crossword Nexus"
    override fun neededHostPermissions(url: URL) =
        listOf("https://crosswordnexus.github.io/*", "https://*.crosswordnexus.com/*")

    override fun matchesUrl(url: URL) =
        url.hostIsDomainOrSubdomainOf("crosswordnexus.com") ||
                (url.hostIsDomainOrSubdomainOf("crosswordnexus.github.io") &&
                        url.pathname.contains("html5-crossword-solver"))

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        val scrapeFn = js("function() { return window.ipuz; }")
        val ipuz = Scraping.executeFunctionForString(tabId, frameId, scrapeFn)
        if (ipuz.isNotEmpty()) {
            return ScrapeResult.Success(listOf(Ipuz(ipuz)))
        }
        return ScrapeResult.Success(listOf())
    }
}