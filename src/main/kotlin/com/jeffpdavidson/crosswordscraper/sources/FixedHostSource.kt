package com.jeffpdavidson.crosswordscraper.sources

import org.w3c.dom.url.URL

/**
 * [Source] whose puzzles come from a fixed source.
 *
 * If the source returns true from [matchesUrl], this function will invoke [scrapePuzzlesWithPermissionGranted] after
 * permission has been granted to access the given URL. This can happen in one of two ways:
 *
 * - If the page is the top-level tab being accessed, then permission is automatically granted to that host.
 * - If the page is embedded in a (i)frame, then [neededHostPermissions] are requested for the URL in that (i)frame.
 *
 * If the source wishes to make additional HTTP requests, it should check necessary permissions for those requests even
 * if they have the same origin as the given URL. This is because different browsers behave differently - while Chrome
 * permits additional HTTP requests on the same host, Firefox does not.
 */
abstract class FixedHostSource : Source {

    /** Host permissions needed to access puzzles for this source at this URL when embedded in a frame. */
    abstract fun neededHostPermissions(url: URL): List<String>

    abstract suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult

    final override suspend fun scrapePuzzles(url: URL, tabId: Int, frameId: Int, isTopLevel: Boolean): ScrapeResult {
        if (!isTopLevel && !hasPermissions(neededHostPermissions(url))) {
            return ScrapeResult.NeedPermissions(neededHostPermissions(url))
        }

        return scrapePuzzlesWithPermissionGranted(url, tabId, frameId)
    }
}