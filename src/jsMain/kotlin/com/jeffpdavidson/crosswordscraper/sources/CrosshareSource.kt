package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.AcrossLite
import com.jeffpdavidson.kotwords.formats.Crosshare
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.url.URL

object CrosshareSource : FixedHostSource() {

    private val PUZZLE_ID_PATTERN = "(?:crosswords|embed)/(.+)".toRegex()

    override val sourceName = "Crosshare"
    override fun neededHostPermissions(url: URL) = listOf("https://*.crosshare.org/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("crosshare.org") &&
                (url.pathname.startsWith("/crosswords/") || (url.pathname.startsWith("/embed/")))
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        // First, try to scrape from the __NEXT_DATA__ JSON on the current page. This works for direct links to
        // Crosshare puzzles, but may contain other data if the user navigated to this page from another Crosshare
        // page.
        val scrapeFn = js(
            """function() {
                return window.__NEXT_DATA__ ? JSON.stringify(window.__NEXT_DATA__) : '';
            }"""
        )
        val nextDataJsonString = Scraping.executeFunctionForString(tabId, frameId, scrapeFn)
        if (nextDataJsonString.isEmpty()) {
            return ScrapeResult.Success(listOf())
        }
        try {
            val crosshare = Crosshare(nextDataJsonString)
            val puzzle = crosshare.asPuzzle()
            // For the main Crosshare site, make sure the puzzle title matches the page title - otherwise, this data
            // is probably for a different puzzle. For embeds, we don't need to do this since there is no navigation
            // to different puzzles.
            if (url.pathname.contains("/embed/")) {
                return ScrapeResult.Success(listOf(crosshare))
            }
            val titleFn = js("function() { return document.title; }")
            val pageTitle = Scraping.executeFunctionForString(tabId, frameId, titleFn)
            if (pageTitle.startsWith(puzzle.title)) {
                return ScrapeResult.Success(listOf(crosshare))
            } else {
                console.info("JSON in __NEXT_DATA__ is for a different puzzle; falling back to API")
            }
        } catch (e: Exception) {
            console.info("Could not load JSON from __NEXT_DATA__; falling back to API")
        }

        // Otherwise, fetch the JSON data directly - this requires a separate fetch, but should always work.
        val nextDataJson = Json.parseToJsonElement(nextDataJsonString)
        val buildId =
            nextDataJson.jsonObject["buildId"]?.jsonPrimitive?.content ?: return ScrapeResult.Success(listOf())
        val matchResult = PUZZLE_ID_PATTERN.find(url.pathname) ?: return ScrapeResult.Success(listOf())
        if (!hasPermissions(neededHostPermissions(url))) {
            return ScrapeResult.NeedPermissions(neededHostPermissions(url))
        }
        val puzzleId = matchResult.groupValues[1]
        val dataUrl = "https://crosshare.org/_next/data/$buildId/crosswords/$puzzleId.json"
        return ScrapeResult.Success(listOf(Crosshare(Http.fetchAsString(dataUrl))))
    }
}