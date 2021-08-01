package com.jeffpdavidson.crosswordscraper.sources

import com.jeffpdavidson.crosswordscraper.Http
import com.jeffpdavidson.crosswordscraper.Scraping
import com.jeffpdavidson.crosswordscraper.sources.Source.Companion.hostIsDomainOrSubdomainOf
import com.jeffpdavidson.kotwords.formats.AcrossLite
import com.jeffpdavidson.kotwords.formats.Jpz
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.w3c.dom.url.URL
import org.w3c.dom.url.URLSearchParams

object CrosswordNexusSource : FixedHostSource() {
    override val sourceName: String = "Crossword Nexus"
    override val neededHostPermissions = listOf("https://*.crosswordnexus.com/*")

    override fun matchesUrl(url: URL): Boolean = url.hostIsDomainOrSubdomainOf("crosswordnexus.com")

    private val PUZZLE_URL_REGEX = "url: '([^']*.)'".toRegex()

    override suspend fun scrapePuzzlesWithPermissionGranted(url: URL, frameId: Int): ScrapeResult {
        // First, see if there's a puzzle URL in one of the query parameters.
        val paramUrls = url.searchParams.values().filter { it.endsWith(".jpz") || it.endsWith(".puz") }
        if (paramUrls.isNotEmpty()) {
            return scrapeUrls(url, paramUrls)
        }

        // Otherwise, look for initialization scripts which include a puzzle URL.
        val getScriptsCommand = """
            JSON.stringify(
                Array.from(
                    window.document.getElementsByTagName(\'script\')
                ).map(elem => elem.innerText)
            )
        """.replace("\n", "")
        val scriptsJson = Scraping.executeCommandForString(frameId, getScriptsCommand)
        val scriptsUrls = Json.decodeFromString(ListSerializer(String.serializer()), scriptsJson)
            .mapNotNull {
                val matchResult = PUZZLE_URL_REGEX.find(it)
                matchResult?.groupValues?.get(1)
            }
        return scrapeUrls(url, scriptsUrls)
    }

    private suspend fun scrapeUrls(baseUrl: URL, urls: List<String>): ScrapeResult.Success {
        val fetchedUrls =
            urls.map { it to Http.fetchAsBinary(URL(it, baseUrl.toString()).toString()) }
                .partition { it.first.endsWith(".jpz") }
        return ScrapeResult.Success(
            puzzles = fetchedUrls.first.map {
                Jpz.fromJpzFile(it.second).asPuzzle()
            },
            crosswords = fetchedUrls.second.map {
                AcrossLite(it.second)
            }
        )
    }
}

// Kotlin's URLSearchParams wrapper doesn't expose a keys() method, so we have to expose it ourselves.
private fun URLSearchParams.values() : Iterable<String> =
    asDynamic().values().unsafeCast<JsIterator<String>>().iterable()

private external interface JsIterator<T> {
    fun next() : JsIteratorResult<T>
}

private external class JsIteratorResult<T> {
    val done : Boolean
    val value : T?
}

private fun <T> JsIterator<T>.iterable() : Iterable<T> {
    return object : Iterable<T> {
        override fun iterator(): Iterator<T> =
            object : Iterator<T> {
                private var elem = this@iterable.next()
                override fun hasNext() = !elem.done
                override fun next(): T {
                    val ret = elem.value ?: throw NoSuchElementException("No more values")
                    elem = this@iterable.next()
                    return ret
                }
            }
    }
}