package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.kotwords.formats.Crosswordable
import org.w3c.dom.url.URL

/** Source of puzzles that can be scraped. */
interface Source {

    /** The name of the source. Used to identify it for permission requests or if no puzzle title is available. */
    val sourceName: String

    /**
     * The host permission needed to scrape a puzzle at the given URL.
     *
     * Technically, no permission is needed to access the top-level document. However, permissions will be needed if
     * either the document is in a frame or if separate fetches need to be done beyond what is already available in the
     * document. Since this is a fairly common case, it's simplest to ask for any permissions that may be needed, even
     * if the document happens to be accessible by virtue of being the top-level document.
     */
    val neededHostPermissions: List<String>

    /**
     * Whether the page at the given URL may contain a puzzle for this source.
     *
     * This method is used as an initial precondition to prevent users from seeing unnecessary permission prompts for
     * pages that are unlikely to contain puzzles.
     */
    fun matchesUrl(url: URL): Boolean

    /**
     * Return a [Crosswordable] for the contents of the page if one could be scraped, or null if none was found.
     *
     * Only called when [matchesUrl] returns true and all permissions required per [getNeededHostPermissions] have been
     * granted.
     */
    suspend fun scrapePuzzle(url: URL, frameId: Int): Crosswordable?

    companion object {
        /** Whether this URL's host is this domain, or a subdomain of this domain. */
        internal fun URL.hostIsDomainOrSubdomainOf(domain: String): Boolean {
            return host == domain || host.endsWith(".$domain")
        }
    }
}