package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.XWordInfo
import com.jeffpdavidson.kotwords.formats.XWordInfoAcrostic
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.w3c.dom.url.URL

object XWordInfoSource : FixedHostSource() {

    override val sourceName = "XWord Info"
    override fun neededHostPermissions(url: URL) = listOf("https://*.xwordinfo.com/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("xwordinfo.com")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        // A normal HTTP fetch doesn't work because the server requires the referer header to be set, and extensions
        // cannot set it (nor is it set to match the page we're acting on). As a workaround, we can run fetches as
        // Javascript functions on the pages themselves.
        if (url.pathname.contains("/Acrostic")) {
            return scrapeAcrostic(tabId, frameId)
        } else if (url.pathname.contains("/Solve")) {
            return scrapePuzzle(tabId, frameId)
        }
        return ScrapeResult.Success(listOf())
    }

    private suspend fun scrapeAcrostic(tabId: Int, frameId: Int): ScrapeResult {
        // Try to read the author from the page HTML.
        val getHeadersFn = js(
            """function() {
                    return JSON.stringify(
                        Array.from(
                            window.document.getElementsByTagName('h2')
                        ).map(function(elem) { return elem.innerText; })
                    );
                }"""
        )
        val headersJson = Scraping.executeFunctionForString(tabId, frameId, getHeadersFn)
        val author = Json.decodeFromString(ListSerializer(String.serializer()), headersJson).firstOrNull {
            it.startsWith("by ")
        } ?: ""

        val getJsonFn = js(
            """function() {
                    var date = new URL(window.location);
                    return $.ajax({
                        url: "https://www.xwordinfo.com/JSON/AcrosticData.ashx",
                        dataType: "json",
                        async: false,
                        data: {
                            date: date.searchParams.get("date")
                        }
                    }).responseText;
                }"""
        )
        val json = Scraping.executeFunctionForString(tabId, frameId, getJsonFn)
        return ScrapeResult.Success(listOf(XWordInfoAcrostic(json = json, author = author)))
    }

    private suspend fun scrapePuzzle(tabId: Int, frameId: Int): ScrapeResult {
        // Extract the input parameters from the initialization script and refetch the data.
        val getJsonFn = js(
            """function() {
                    var script = Array.from(document.getElementsByTagName("script")).map(function(scriptTag) {
                        return scriptTag.innerText;
                    }).find(function(script) {
                        return script.includes("xwInterAct");
                    }, "")
                    if (script === "") {
                        return "";
                    }
                    var dataRegex = /Go\(({[^}]+})\);/
                    var result = dataRegex.exec(script);
                    if (result === null) {
                        return "";
                    }
                    var data = JSON.parse(result[1].replaceAll(/([a-zA-Z]+):/g, '"$1":'));

                    return $.ajax({
                        url: "https://www.xwordinfo.com/JSON/data.ashx",
                        dataType: "json",
                        async: false,
                        data: data
                    }).responseText;
                }"""
        )
        val json = Scraping.executeFunctionForString(tabId, frameId, getJsonFn)
        if (json.isNotEmpty()) {
            return ScrapeResult.Success(listOf(XWordInfo(json)))
        }
        return ScrapeResult.Success(listOf())
    }
}