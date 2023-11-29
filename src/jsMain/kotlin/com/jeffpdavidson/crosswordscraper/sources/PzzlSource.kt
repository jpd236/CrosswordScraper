package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.Pzzl
import org.w3c.dom.url.URL

object PzzlSource : FixedHostSource() {

    private data class PzzlSourceInfo(val hostPermission: String, val baseUrl: String)

    private val NYT_SOURCE = PzzlSourceInfo(
        hostPermission = "https://*.pzzl.com/*",
        baseUrl = "https://nytsyn.pzzl.com/nytsyn-crossword-mh/nytsyncrossword",
    )
    private val NEWSDAY_SOURCE = PzzlSourceInfo(
        hostPermission = "https://*.brainsonly.com/*",
        baseUrl = "https://www.brainsonly.com/servlets-newsday-crossword/newsdaycrossword",
    )

    override val sourceName: String = "PZZL"
    override fun matchesUrl(url: URL): Boolean {
        return getSourceInfo(url) != null
    }

    override fun neededHostPermissions(url: URL): List<String> {
        return listOf(getSourceInfo(url)?.hostPermission ?: throw UnsupportedOperationException("Unknown URL: $url"))
    }

    private fun getSourceInfo(url: URL): PzzlSourceInfo? {
        if (url.host == "nytsyn.pzzl.com" && url.pathname.matches("/cwd[^/]*/".toRegex())) {
            return NYT_SOURCE
        }
        if (url.hostIsDomainOrSubdomainOf("brainsonly.com") && url.pathname == "/global/newsday/cwd/") {
            return NEWSDAY_SOURCE
        }
        return null
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        val baseUrl = getSourceInfo(url)?.baseUrl ?: throw UnsupportedOperationException("Unknown URL: $url")
        val data = Http.fetchAsString("$baseUrl?date=${url.hash.substringAfterLast("/")}")
        return ScrapeResult.Success(listOf(Pzzl(data)))
    }
}