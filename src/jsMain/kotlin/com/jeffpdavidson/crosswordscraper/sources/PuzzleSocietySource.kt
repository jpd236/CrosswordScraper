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
import org.w3c.dom.parsing.DOMParser
import org.w3c.dom.url.URL

object PuzzleSocietySource : FixedHostSource() {

    override val sourceName: String = "Puzzle Society"
    override fun neededHostPermissions(url: URL) =
        listOf("https://*.puzzlesociety.com/*", "https://*.amuniversal.com/*")

    private val DATE_FORMAT = DateFormat("yyyy-MM-dd")
    private val JSON = Json {
        ignoreUnknownKeys = true
    }

    override fun matchesUrl(url: URL): Boolean {
        return url.hostIsDomainOrSubdomainOf("puzzlesociety.com")
    }

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, tabId: Int, frameId: Int): ScrapeResult {
        // First, try reading the JSON from the page's __NEXT_DATA__ directly.
        val scrapeFn = js(
            """function() {
                var data = window.__NEXT_DATA__;
                return data ? JSON.stringify(data) : '';
            }"""
        )
        val dataJson = Scraping.executeFunctionForString(tabId, frameId, scrapeFn)
        var data = if (dataJson.isEmpty()) null else getDataIfMatching(dataJson, url)
        if (data == null) {
            // Data doesn't match, so fetch the current page and read the embedded __NEXT_DATA__ initializer script.
            val urlPermissions = neededHostPermissions(url) + getPermissionsForUrls(listOf(url))
            if (!hasPermissions(urlPermissions)) {
                return ScrapeResult.NeedPermissions(neededHostPermissions(url))
            }
            val html = Http.fetchAsString(url.toString())
            val parser = DOMParser()
            val document = parser.parseFromString(html, "text/html")
            val nextDataJson = document.getElementById("__NEXT_DATA__")?.textContent ?: ""
            if (nextDataJson.isEmpty()) {
                // No data on refetched page - nothing more to try. Assume there's no puzzle here.
                return ScrapeResult.Success()
            }
            data = getDataIfMatching(nextDataJson, url)
        }
        if (data == null || data.props.pageProps.gameContent.gameLevelDataSets.isEmpty()) {
            // If there's no gameContent, assume there's no puzzle here.
            return ScrapeResult.Success()
        }
        val levelData = data.props.pageProps.gameContent.gameLevelDataSets[0]
        val dateText = levelData.issueDate
        val date = if (dateText.isNotEmpty()) DATE_FORMAT.parseDate(dateText) else DateTime.now().date
        val xmlFiles = levelData.files.filter { it.mimeType in listOf("text/html", "application/xml") }
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
                // Detect the XML format from the contents. The Modern Crossword uses JPZ; others use Uclick XML.
                if (puzzleXml.contains("<crossword-compiler")) {
                    UclickJpz(puzzleXml, date)
                } else {
                    UclickXml(puzzleXml, date)
                }
            )
        )
    }

    private fun getDataIfMatching(dataJson: String, url: URL): Data? {
        val data = JSON.decodeFromString<Data>(dataJson)
        val pathParts = url.pathname.split("/")
        return if (data.query.game.isNotEmpty() &&
            pathParts.size > data.query.game.size &&
            data.query.game == pathParts.subList(pathParts.size - data.query.game.size, pathParts.size)
        ) data else null
    }

    @Serializable
    private data class Data(
        val props: Props,
        val query: Query,
    ) {
        @Serializable
        data class Props(val pageProps: PageProps) {
            @Serializable
            data class PageProps(val gameContent: GameContent = GameContent()) {
                @Serializable
                data class GameContent(val gameLevelDataSets: List<LevelData> = listOf()) {
                    @Serializable
                    data class LevelData(
                        val issueDate: String,
                        val files: List<File>
                    ) {
                        @Serializable
                        data class File(
                            val url: String,
                            val mimeType: String,
                        )
                    }
                }
            }
        }

        @Serializable
        data class Query(val game: List<String> = listOf())
    }
}
