package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.Crosswordable
import com.jeffpdavidson.kotwords.formats.UclickJson
import org.w3c.dom.url.URL

object UniversalSource : Source {

    override val sourceName: String = "Universal Uclick"
    override val neededHostPermissions = listOf("https://*.universaluclick.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.host == "embed.universaluclick.com"
    }

    override suspend fun scrapePuzzle(url: URL, frameId: Int): Crosswordable? {
        val puzzleJson = Scraping.readGlobalJson(frameId, "crossword.jsonData")
        if (puzzleJson.isNotEmpty()) {
            return UclickJson(puzzleJson)
        }
        return null
    }
}