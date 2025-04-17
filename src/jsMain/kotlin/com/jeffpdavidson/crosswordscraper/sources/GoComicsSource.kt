package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.UclickJpz
import com.jeffpdavidson.kotwords.formats.UclickXml
import korlibs.time.DateFormat
import korlibs.time.DateTime
import korlibs.time.parseDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.asList
import org.w3c.dom.parsing.DOMParser
import org.w3c.dom.url.URL
import org.w3c.files.FileReaderSync

object GoComicsSource : FixedHostSource() {

    override val sourceName: String = "GoComics"
    override fun neededHostPermissions(url: URL) =
        listOf("https://*.gocomics.com/*", "https://*.amuniversal.com/*")

    private val DATE_FORMAT = DateFormat("yyyy-MM-dd'T'HH:mm:ss")

    @Suppress("RegExpRedundantEscape") // https://youtrack.jetbrains.com/issue/KTIJ-33836
    private val NEXT_F_PATTERN = "self\\.__next_f\\.push\\(\\[1,(.*)\\]\\)".toRegex()
    private val JSON = Json {
        ignoreUnknownKeys = true
    }

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("gocomics.com") && url.pathname.startsWith("/puzzles/")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        // Read the date from the crossword <iframe> for verification.
        val scrapeDateFn = js(
            """function() {
                var crosswordFrame = document.querySelector('iframe[data-level-id]');
                if (!crosswordFrame) {
                    return '';
                }
                return crosswordFrame.getAttribute('data-level-id');
            }"""
        )
        val pageDate = Scraping.executeFunctionForString(tabId, frameId, scrapeDateFn)
        if (pageDate.isEmpty()) {
            // Can't read date from page, so can't verify any present data is for the right date.
            return ScrapeResult.Success()
        }

        // First, try reading the JSON from the page directly.
        val scrapeFn = js(
            """function() {
                if (!window.__next_f) {
                    return '';
                }
                var dataElems = window.__next_f.filter(
                    function(elem) {
                        return elem.length > 1 && typeof elem[1] === 'string'
                    }
                );
                return dataElems.reduce(function(acc, elem) { return acc + elem[1] }, '');
            }"""
        )
        val dataJson = Scraping.executeFunctionForString(tabId, frameId, scrapeFn)
        var data = if (dataJson.isEmpty()) null else getDataIfMatching(dataJson, pageDate, url)
        if (data == null) {
            // Data doesn't match, so fetch the current page and read the embedded initializer script.
            val urlPermissions = neededHostPermissions(url) + getPermissionsForUrls(listOf(url))
            if (!hasPermissions(urlPermissions)) {
                return ScrapeResult.NeedPermissions(neededHostPermissions(url))
            }
            val html = Http.fetchAsString(url.toString())
            val parser = DOMParser()
            val document = parser.parseFromString(html, "text/html")
            val scriptElems = document.getElementsByTagName("script")
            val scriptDataJson = scriptElems.asList().joinToString("") {
                NEXT_F_PATTERN.matchEntire(it.textContent ?: "")?.let { matchResult ->
                    // Parse and unparse as JSON to handle escape characters, since this is a Javascript string literal.
                    JSON.parseToJsonElement(matchResult.groupValues[1]).jsonPrimitive.content
                } ?: ""
            }
            if (scriptDataJson.isEmpty()) {
                // No data on refetched page - nothing more to try. Assume there's no puzzle here.
                return ScrapeResult.Success()
            }
            data = getDataIfMatching(scriptDataJson, pageDate, url)
        }
        if (data == null || data.files.isEmpty()) {
            // If there's no gameContent, assume there's no puzzle here.
            return ScrapeResult.Success()
        }
        val dateText = data.issueDate
        val date = if (dateText.isNotEmpty()) DATE_FORMAT.parseDate(dateText) else DateTime.now().date
        val xmlFiles = data.files.filter {
            it.originalFileName.contains(".xml") && !it.originalFileName.contains("title")
        }
        if (xmlFiles.isEmpty() || xmlFiles.last().url.isEmpty()) {
            return ScrapeResult.Error("No file found in level data")
        }
        val xmlUrl = xmlFiles.last().url
        val neededPermissions = getPermissionsForUrls(listOf(URL(xmlUrl)))
        if (!hasPermissions(neededPermissions)) {
            return ScrapeResult.NeedPermissions(neededPermissions)
        }
        val puzzleXml = Http.fetchAsString(xmlUrl)
        if (puzzleXml.isEmpty()) {
            return ScrapeResult.Error("Could not fetch puzzle XML")
        }
        return ScrapeResult.Success(
            listOf(
                // Detect the XML format from the contents.
                if (puzzleXml.contains("<crossword-compiler")) {
                    UclickJpz(puzzleXml, date)
                } else {
                    UclickXml(puzzleXml, date)
                }
            )
        )
    }

    private fun getDataIfMatching(data: String, pageDate: String, url: URL): LevelData? {
        val dataJsonString = data.lines().firstOrNull { it.contains("levelData") }?.substringAfter(":") ?: return null
        val dataJson = JSON.parseToJsonElement(dataJsonString)
        val pathParts = url.pathname.split("/")
        val puzzleSlug = findValueWithKey(dataJson, "puzzleSlug") ?: return null
        if (!pathParts.contains((puzzleSlug as JsonPrimitive).content)) {
            return null
        }
        val levelData = findValueWithKey(dataJson, "levelData")
        return levelData?.let {
            JSON.decodeFromJsonElement<List<LevelData>>(it)
        }?.firstOrNull {
            it.issueDate == pageDate
        }
    }

    private fun findValueWithKey(element: JsonElement, key: String): JsonElement? {
        when (element) {
            is JsonPrimitive -> return null
            is JsonObject -> {
                val value = element[key]
                if (value != null) {
                    return value
                }
                val children = element["children"]
                if (children != null) {
                    return findValueWithKey(children, key)
                }
                return null
            }

            is JsonArray -> return element.firstNotNullOfOrNull { findValueWithKey(it, key) }
        }
    }

    @Serializable
    private data class LevelData(
        val issueDate: String,
        val files: List<File>,
    ) {
        @Serializable
        data class File(
            val url: String,
            val originalFileName: String,
        )
    }
}
