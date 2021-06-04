package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.Crosswordable
import com.jeffpdavidson.kotwords.formats.WorldOfCrosswords
import org.w3c.dom.url.URL
import kotlin.js.Date

object WorldOfCrosswordsSource : Source {

    override val sourceName = "World of Crosswords"
    override val neededHostPermissions = listOf("https://*.worldofcrosswords.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("worldofcrosswords.com")
                && (url.pathname == "/" || url.pathname == "/index.php")
    }

    override suspend fun scrapePuzzle(url: URL, frameId: Int): Crosswordable {
        // Map /, /index.php -> /getEmptyPuzzle.php
        val emptyPuzzleUrl = URL(url.toString())
        emptyPuzzleUrl.pathname = url.pathname.replace("/(?:index\\.php)?".toRegex(), "/getEmptyPuzzle.php")

        val response = Http.fetchAsString(emptyPuzzleUrl.toString())
        return WorldOfCrosswords(response, year = Date().getFullYear(), author = "World of Crosswords", copyright = "")
    }
}