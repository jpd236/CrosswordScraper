package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.kotwords.formats.WashingtonPost
import org.w3c.dom.url.URL

/*
 * since URLs to multiple puzzles can be found on the page, we select the correct one by:
 * * counting displayed grid cells (i.e., looking at the actual puzzle grid rendered on screen); if the number of matching puzzles is:
 *   * 0: return no puzzle
 *   * 1: return that 1 puzzle
 *   * 2: break the tie by comparing (locked-cell) patterns (with rendered grid); if the number of matching puzzles is
 *     * 0: return no puzzle
 *     * 1: return that 1 puzzle
 *     * 2: break the tie by returning the most recently fetched puzzle
 */
object WashingtonPostSource : FixedHostSource() {

    override val sourceName = "Washington Post"
    override fun neededHostPermissions(url: URL) = listOf("https://*.wapo.pub/*")

    override fun matchesUrl(url: URL): Boolean {
        // only match iframe URL to avoid duplicate processing
        // main pages load the iframe that we will scrape
        return url.pathname.contains("/games-static/games-crossword/")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        // look through all frames to find the crossword iframe and check if it has loaded puzzle data
        val (allTabId, frames) = Scraping.getAllFrames()
        val crosswordFrame = frames.firstOrNull {
            it.url.contains("/games-static/games-crossword/")
        }

        val puzzleUrl = crosswordFrame?.let {
            // try to extract *all* puzzle URLs from the iframe's resources and detect which is displayed
            val scrapeFn = js(
                """function() {
                    // get puzzle URLs with their fetch times from performance API
                    var puzzleUrlsWithTime = [];
                    if (window.performance && window.performance.getEntriesByType) {
                        var entries = window.performance.getEntriesByType('resource');
                        for (var i = 0; i < entries.length; i++) {
                            var entry = entries[i];
                            if (entry.name && entry.name.includes('games-service-prod.site.aws.wapo.pub/crossword/levels/')) {
                                puzzleUrlsWithTime.push({
                                    url: entry.name,
                                    startTime: entry.startTime
                                });
                            }
                        }
                    }

                    // count grid cells actually displayed
                    var gridCells = document.querySelectorAll('button[class*="relative flex size-full"]');
                    var displayedCellCount = gridCells.length;

                    // get localStorage to match with displayed puzzle
                    var activePuzzleType = null;
                    try {
                        var storage = JSON.parse(window.localStorage.getItem('crossword-storage'));
                        if (storage && storage.unfinishedSessions) {
                            var sessions = storage.unfinishedSessions;
                            var matches = [];

                            // find sessions that match the displayed cell count
                            for (var key in sessions) {
                                if (sessions.hasOwnProperty(key)) {
                                    var session = sessions[key];
                                    if (session.cells && session.cells.length === displayedCellCount) {
                                        matches.push({
                                            key: key,
                                            session: session
                                        });
                                    }
                                }
                            }

                            // if we have multiple matches, use locked-cell pattern as tie breaker
                            if (matches.length === 1) {
                                activePuzzleType = matches[0].key;
                            } else if (matches.length > 1) {
                                // compare locked cell patterns to find the exact match
                                // get locked positions from displayed grid (cells with no number)
                                var displayedPattern = [];
                                for (var i = 0; i < gridCells.length; i++) {
                                    var cell = gridCells[i];
                                    var isEmpty = cell.textContent.trim() === '';
                                    displayedPattern.push(isEmpty);
                                }

                                // find sessions with matching locked cell pattern
                                var patternMatches = [];
                                for (var j = 0; j < matches.length; j++) {
                                    var match = matches[j];
                                    var sessionPattern = match.session.cells.map(function(c) {
                                        return c.type === 'locked';
                                    });

                                    var matchesPattern = true;
                                    for (var k = 0; k < sessionPattern.length && k < displayedPattern.length; k++) {
                                        if (sessionPattern[k] !== displayedPattern[k]) {
                                            matchesPattern = false;
                                            break;
                                        }
                                    }

                                    if (matchesPattern) {
                                        patternMatches.push(match);
                                    }
                                }

                                // if still multiple matches, use performance API to find which was fetched most recently
                                if (patternMatches.length === 1) {
                                    activePuzzleType = patternMatches[0].key;
                                } else if (patternMatches.length > 1) {
                                    // find which session key matches a fetched URL with the highest startTime
                                    var bestMatch = null;
                                    var bestStartTime = -1;
                                    for (var m = 0; m < patternMatches.length; m++) {
                                        var sessionKey = patternMatches[m].key;
                                        // find URL that contains this session key pattern
                                        for (var n = 0; n < puzzleUrlsWithTime.length; n++) {
                                            var urlData = puzzleUrlsWithTime[n];
                                            // check if URL contains the session key (e.g., "sunday/2025/11/02")
                                            if (urlData.url.includes(sessionKey)) {
                                                if (urlData.startTime > bestStartTime) {
                                                    bestStartTime = urlData.startTime;
                                                    bestMatch = sessionKey;
                                                }
                                            }
                                        }
                                    }
                                    activePuzzleType = bestMatch;
                                }
                            }
                        }
                    } catch (e) {
                        // localStorage parsing failed, will fall back to startTime
                    }

                    return JSON.stringify({
                        urlsWithTime: puzzleUrlsWithTime,
                        activePuzzleType: activePuzzleType,
                        cellCount: displayedCellCount
                    });
                }"""
            )

            val resultJson = Scraping.executeFunctionForString(allTabId, it.frameId, scrapeFn)
            if (resultJson.isNotEmpty() && resultJson != "{}") {
                val result = JSON.parse<dynamic>(resultJson)
                val urlsWithTime = result.urlsWithTime as Array<dynamic>
                val activePuzzleTypeOrNull = result.activePuzzleType as? String

                // first, try to match using localStorage-detected puzzle type
                val matchedUrlOrNull = activePuzzleTypeOrNull?.let { activePuzzleType ->
                    // Extract the type from the key (e.g., "sunday/2025/11/02" -> "sunday")
                    val puzzleTypeOrNull = activePuzzleType.split("/").firstOrNull()
                    puzzleTypeOrNull?.let { puzzleType ->
                        // Find URL matching this type
                        urlsWithTime.map { urlWithTime -> urlWithTime.url as String }.firstOrNull { url ->
                            url.contains("/$puzzleType/")
                        }
                    }
                }

                // Return matched URL or fallback to most recently fetched puzzle (highest startTime)
                matchedUrlOrNull ?: if (urlsWithTime.isNotEmpty()) {
                    urlsWithTime.maxByOrNull { urlWithTime -> urlWithTime.startTime as Double }?.url as? String
                } else {
                    null
                }
            } else {
                null
            }
        } ?: return ScrapeResult.Success() // no puzzles found

        val neededPermissions = getPermissionsForUrls(listOf(URL(puzzleUrl)))
        if (!hasPermissions(neededPermissions)) {
            return ScrapeResult.NeedPermissions(neededPermissions)
        }

        return try {
            ScrapeResult.Success(listOf(WashingtonPost(Http.fetchAsString(puzzleUrl))))
        } catch (e: Http.HttpException) {
            // TODO? avoid swallowing exception
            ScrapeResult.Success()
        }
    }
}