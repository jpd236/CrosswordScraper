package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.Jpz
import org.w3c.dom.url.URL

object CrosswordCompilerSource : Source {

    override val sourceName: String = "Crossword Compiler"

    override fun matchesUrl(url: URL): Boolean = url.protocol == "http:" || url.protocol == "https:"

    private val knownIframeHosts = listOf(
        "apps.washingtonexaminer.com",
        "www.brendanemmettquigley.com"
    )

    override suspend fun scrapePuzzles(url: URL, frameId: Int, isTopLevel: Boolean): ScrapeResult {
        // We can't access the frame contents of internal frames without requesting permissions up front, so check
        // against a list of known hosts which may have iframes containing CrosswordCompiler applets. Otherwise, we
        // just assume no applet is present to avoid asking for permissions on every page.
        if (!isTopLevel) {
            if (knownIframeHosts.contains(url.hostname)) {
                val neededPermissions = getPermissionsForUrls(listOf(url))
                if (!hasPermissions(neededPermissions)) {
                    return ScrapeResult.NeedPermissions(neededPermissions)
                }
            } else {
                return ScrapeResult.Success(listOf())
            }
        }

        val puzzleData = "" // Scraping.readGlobalString(frameId, "CrosswordPuzzleData")
        if (puzzleData.isEmpty()) {
            return ScrapeResult.Success(listOf())
        }
        return ScrapeResult.Success(listOf(Jpz.fromXmlString(puzzleData)))
    }
}