package com.jeffpdavidson.crosswordscraper.sources

import browser.permissions.Permissions
import kotlinx.coroutines.await
import org.w3c.dom.url.URL

/** Source of puzzles that can be scraped. */
interface Source {

    /** The name of the source. Used to identify it for permission requests or if no puzzle title is available. */
    val sourceName: String

    /**
     * Whether the page at the given URL may contain a puzzle for this source.
     *
     * This method is used as an initial precondition to prevent users from seeing unnecessary permission prompts for
     * pages that are unlikely to contain puzzles.
     */
    fun matchesUrl(url: URL): Boolean

    /**
     * Scrape the contents of the page and return the result.
     *
     * Only called when [matchesUrl] returns true.
     *
     * If host permissions are needed, return a [ScrapeResult.NeedPermissions] indicating which permissions are
     * necessary.
     */
    suspend fun scrapePuzzles(url: URL, frameId: Int, isTopLevel: Boolean): ScrapeResult

    /** Whether the given permissions have been granted. */
    suspend fun hasPermissions(neededPermissions: List<String>): Boolean =
        browser.permissions.contains(Permissions { origins = neededPermissions.toTypedArray() }).await()

    companion object {
        /** Whether this URL's host is this domain, or a subdomain of this domain. */
        internal fun URL.hostIsDomainOrSubdomainOf(domain: String): Boolean {
            return host == domain || host.endsWith(".$domain")
        }
    }
}