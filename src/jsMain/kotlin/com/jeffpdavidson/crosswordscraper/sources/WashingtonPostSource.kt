package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.WashingtonPost
import korlibs.time.DateFormat
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.w3c.dom.url.URL

object WashingtonPostSource : FixedHostSource() {

    override val sourceName = "Washington Post"
    override fun neededHostPermissions(url: URL) = listOf("https://*.wapo.pub/*")

    override fun matchesUrl(url: URL): Boolean {
        return url.hostname == "discovery-games-portal-prod-cdn.site.aws.wapo.pub" ||
                (url.hostname == "www.washingtonpost.com" && url.pathname.contains("/games-crossword/"))
    }

    private val MODAL_DATE_FORMAT = DateFormat("MMM d, YYYY")
    private val URL_DATE_FORMAT = DateFormat("YYYY/MM/dd")

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        val scrapeFn = js(
            """function() {
                return JSON.stringify(
                    Array.from(
                        window.document.getElementsByClassName('wpds-modal')
                    ).map(function(elem) { return elem.innerText; })
                );
            }"""
        )
        val modalsJson = Scraping.executeFunctionForString(tabId, frameId, scrapeFn)
        val modals = Json.decodeFromString(ListSerializer(String.serializer()), modalsJson)
        val puzzleLink = modals.firstNotNullOfOrNull { modal ->
            val date = modal.lines().firstNotNullOfOrNull { line ->
                MODAL_DATE_FORMAT.tryParse(line, doThrow = false, doAdjust = false)
            }
            date?.let {
                val source = if (modal.contains("Daily crosswords")) "daily" else "sunday"
                val urlDate = URL_DATE_FORMAT.format(it)
                "https://games-service-prod.site.aws.wapo.pub/crossword/levels/$source/$urlDate"
            }
        }
        return puzzleLink?.let {
            val neededPermissions = getPermissionsForUrls(listOf(URL(it)))
            if (!hasPermissions(neededPermissions)) {
                ScrapeResult.NeedPermissions(neededPermissions)
            }
            ScrapeResult.Success(listOf(WashingtonPost(Http.fetchAsString(it))))
        } ?: ScrapeResult.Success()
    }
}