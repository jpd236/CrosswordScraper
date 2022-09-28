package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.UclickXml
import com.soywiz.klock.Date
import com.soywiz.klock.DateFormat
import com.soywiz.klock.parseDate
import org.w3c.dom.url.URL

object PuzzleSocietySource : FixedHostSource() {

    override val sourceName: String = "Puzzle Society"
    override val neededHostPermissions = listOf("https://crossword-game.azureedge.net/*")

    private val DATE_FORMAT = DateFormat("yyyy-MM-dd")

    override fun matchesUrl(url: URL): Boolean {
        return url.host == "crossword-game.azureedge.net"
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        val scrapeFn = js(
            """function() {
                var xmlData = game.scene.getScene('Crossword').crossword.xmlData.documentElement.outerHTML;
                return xmlData ? xmlData : '';
            }"""
        )
        val puzzleXml = Scraping.executeFunctionForString(tabId, frameId, scrapeFn)
        if (puzzleXml.isNotEmpty()) {
            val dateFn =  js("function() { return levelData.date ? levelData.date : ''; }")
            val date = Scraping.executeFunctionForString(tabId, frameId, dateFn)
            return ScrapeResult.Success(listOf(UclickXml(puzzleXml, DATE_FORMAT.parseDate(date))))
        }
        return ScrapeResult.Success(listOf())
    }
}
