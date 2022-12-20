package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.Jpz
import com.jeffpdavidson.kotwords.formats.UclickJpz
import com.jeffpdavidson.kotwords.formats.UclickXml
import com.soywiz.klock.Date
import com.soywiz.klock.DateFormat
import com.soywiz.klock.DateTime
import com.soywiz.klock.parseDate
import org.w3c.dom.url.URL

object PuzzleSocietySource : FixedHostSource() {

    override val sourceName: String = "Puzzle Society"
    override fun neededHostPermissions(url: URL) = listOf("https://crossword-game.azureedge.net/*")

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
            val dateText = Scraping.executeFunctionForString(tabId, frameId, dateFn)
            val date = if (dateText.isNotEmpty()) DATE_FORMAT.parseDate(dateText) else DateTime.now().date
            return ScrapeResult.Success(listOf(
                // Detect the XML format from the contents. The Modern Crossword uses JPZ; others use Uclick XML.
                if (puzzleXml.contains("<crossword-compiler")) {
                    UclickJpz(puzzleXml, date)
                } else {
                    UclickXml(puzzleXml, date)
                }
            ))
        }
        return ScrapeResult.Success(listOf())
    }
}
