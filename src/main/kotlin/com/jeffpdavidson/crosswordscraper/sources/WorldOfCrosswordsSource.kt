package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.WorldOfCrosswords
import org.w3c.dom.url.URL
import kotlin.js.Date

object WorldOfCrosswordsSource : FixedHostSource() {

    override val sourceName = "World of Crosswords"
    override val neededHostPermissions = listOf("https://*.worldofcrosswords.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("worldofcrosswords.com")
                && (url.pathname == "/" || url.pathname == "/index.php")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, frameId: Int): ScrapeResult {
        // We do an unconditional HTTP fetch, so we always need permissions, even in the top-level frame.
        if (!hasPermissions(neededHostPermissions)) {
            return ScrapeResult.NeedPermissions(neededHostPermissions)
        }

        // Map /, /index.php -> /getEmptyPuzzle.php
        val emptyPuzzleUrl = URL(url.toString())
        emptyPuzzleUrl.pathname = url.pathname.replace("/(?:index\\.php)?".toRegex(), "/getEmptyPuzzle.php")

        val response = Http.fetchAsString(emptyPuzzleUrl.toString())
        return ScrapeResult.Success(
            listOf(
                WorldOfCrosswords(response, year = Date().getFullYear(), author = "World of Crosswords", copyright = "")
            )
        )
    }
}