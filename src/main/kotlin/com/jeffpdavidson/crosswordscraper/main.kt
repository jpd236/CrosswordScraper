package com.jeffpdavidson.crosswordscraper

import browser.permissions.Permissions
import com.jeffpdavidson.crosswordscraper.sources.AmuseLabsSource
import com.jeffpdavidson.crosswordscraper.sources.BostonGlobeSource
import com.jeffpdavidson.crosswordscraper.sources.CrosshareSource
import com.jeffpdavidson.crosswordscraper.sources.UniversalSource
import com.jeffpdavidson.crosswordscraper.sources.WallStreetJournalSource
import com.jeffpdavidson.crosswordscraper.sources.WorldOfCrosswordsSource
import com.jeffpdavidson.kotwords.formats.Crosswordable
import com.jeffpdavidson.kotwords.model.Crossword
import kotlinx.browser.document
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.dom.addClass
import kotlinx.dom.clear
import kotlinx.dom.removeClass
import kotlinx.html.HtmlBlockTag
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.dom.append
import kotlinx.html.js.li
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.p
import kotlinx.html.js.ul
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.FileReader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

fun main() {
    document.onContentLoadedEventAsync {
        launchCrosswordScraper()
    }
}

private val SOURCES = listOf(
    AmuseLabsSource,
    BostonGlobeSource,
    CrosshareSource,
    UniversalSource,
    WallStreetJournalSource,
    WorldOfCrosswordsSource,
)

private suspend fun launchCrosswordScraper() {
    val puzzles = scrapePuzzles()

    val puzzleContainer = document.getElementById("puzzle-container") as HTMLDivElement
    puzzleContainer.append {
        if (puzzles.isEmpty()) {
            p {
                +"No puzzles found!"
            }
        } else {
            ul(classes = "list-group") {
                puzzles.forEach { scrapedPuzzle ->
                    li(classes = "list-group-item") {
                        when (scrapedPuzzle) {
                            is ScrapeResult.Success -> renderScrapeSuccess(scrapedPuzzle)
                            is ScrapeResult.Error -> renderScrapeError(scrapedPuzzle)
                            is ScrapeResult.NeedPermissions -> renderPermissionPrompt(scrapedPuzzle) { granted ->
                                // In practice, the popup seems to be closed after a permission prompt, but
                                // in case that changes, retry the scrape if the permission was granted.
                                // See https://bugs.chromium.org/p/chromium/issues/detail?id=952645.
                                if (granted) {
                                    puzzleContainer.clear()
                                    GlobalScope.launch {
                                        launchCrosswordScraper()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val loadingContainer = document.getElementById("loading-container") as HTMLDivElement
    loadingContainer.addClass("d-none")
    puzzleContainer.removeClass("d-none")
}

/** Scrape puzzles from all frames in the user's active tab. */
private suspend fun scrapePuzzles(): Set<ScrapeResult> {
    val frames = Scraping.getAllFrames()
    val puzzles = mutableSetOf<ScrapeResult>()
    // TODO: Investigate whether parallelizing scrapes improves performance on pages with many puzzles.
    frames.forEach { frame ->
        SOURCES.forEach { source ->
            val url = URL(frame.url)
            if (source.matchesUrl(url)) {
                val neededPermissions = source.neededHostPermissions
                val hasPermissions = neededPermissions.isEmpty() ||
                        browser.permissions.contains(Permissions { origins = neededPermissions.toTypedArray() }).await()
                if (!hasPermissions) {
                    puzzles.add(ScrapeResult.NeedPermissions(source.sourceName, neededPermissions))
                } else {
                    try {
                        val puzzle = source.scrapePuzzle(url, frame.frameId)
                        if (puzzle != null) {
                            puzzles.add(createOutputFiles(source.sourceName, puzzle))
                        }
                    } catch (t: Throwable) {
                        console.error("Error scraping puzzle for source ${source.sourceName}", t.stackTraceToString())
                        puzzles.add(ScrapeResult.Error(source.sourceName))
                    }
                }
            }
        }
    }
    return puzzles
}

private fun HtmlBlockTag.renderScrapeSuccess(scrapedPuzzle: ScrapeResult.Success) {
    // Title to display in the UI - in descending priority, title, then author, then scraping source.
    val title = scrapedPuzzle.crossword.title.ifEmpty {
        scrapedPuzzle.crossword.author.ifEmpty {
            scrapedPuzzle.source
        }
    }
    div(classes = "mb-1") {
        +title
    }

    // Filename to use with the output files - in descending priority, author-title, title, author, scraping source.
    // Each word is capitalized, and non-alphanumeric characters are removed.
    val fileTitleParts =
        listOf(scrapedPuzzle.crossword.author, scrapedPuzzle.crossword.title)
            .filterNot { it.isEmpty() }
    val filenameBase =
        if (fileTitleParts.isEmpty()) {
            scrapedPuzzle.source
        } else {
            fileTitleParts.joinToString("-") { part ->
                part.split("\\s+".toRegex()).joinToString("") {
                    it.replace("[^A-Za-z0-9]".toRegex(), "").replaceFirstChar { ch -> ch.uppercase() }
                }
            }
        }

    // Render each download link labeled by the extension, separated by "|".
    scrapedPuzzle.output.entries.forEachIndexed { i, (format, dataUrl) ->
        if (i > 0) {
            +" | "
        }
        a {
            attributes["download"] = "$filenameBase.${format.extension}"
            href = dataUrl
            +format.extension.uppercase()
        }
    }
}

private fun HtmlBlockTag.renderScrapeError(scrapedPuzzle: ScrapeResult.Error) {
    classes = classes + "disabled"
    div(classes = "mb-1") {
        +scrapedPuzzle.source
    }
    +"Scrape error"
}

private fun HtmlBlockTag.renderPermissionPrompt(
    scrapedPuzzle: ScrapeResult.NeedPermissions,
    onGrantFn: (Boolean) -> Unit,
) {
    div(classes = "mb-1") {
        +scrapedPuzzle.source
    }
    a {
        href = "#"
        onClickFunction = {
            browser.permissions.request(Permissions {
                origins = scrapedPuzzle.permissions.toTypedArray()
            }).then { onGrantFn(it) }
        }
        +"Grant Permission"
    }
}

/**
 * Attempt to create all supported output files for the given [Crosswordable].
 *
 * @return [ScrapeResult.Success] containing all output files that were successfully created. Returns
 *   [ScrapeResult.Error] if the crosswordable fails to generate a [Crossword], or if the given [Crossword] fails
 *   to convert to all supported formats.
 */
private suspend fun createOutputFiles(source: String, crosswordable: Crosswordable): ScrapeResult {
    val crossword = try {
        crosswordable.asCrossword()
    } catch (t: Throwable) {
        console.error("Error converting $source data to crossword:", t.stackTraceToString())
        return ScrapeResult.Error(source)
    }

    // TODO: Investigate whether parallelizing conversions improves performance.
    val output = FileFormat.values().mapNotNull { fileFormat ->
        try {
            fileFormat to createDataUrl(fileFormat.toBinary(crossword))
        } catch (t: Throwable) {
            console.info("Error converting to ${fileFormat.name}:", t.stackTraceToString())
            null
        }
    }.toMap()

    if (output.isEmpty()) {
        console.error("Could not convert to any supported format")
        return ScrapeResult.Error(source)
    }

    return ScrapeResult.Success(source, crossword, output)
}

/** Construct a data URL containing the given data. */
private suspend fun createDataUrl(data: ByteArray): String {
    val blob = Blob(arrayOf(Int8Array(data.toTypedArray()).buffer))
    val reader = FileReader()
    return suspendCoroutine { cont ->
        reader.onload = { _ ->
            cont.resume(reader.result)
        }
        reader.onerror = { _ ->
            cont.resumeWithException(
                Exception("Failed to create data URL, error ${reader.error.name}: ${reader.error.message}")
            )
        }
        reader.readAsDataURL(blob)
    }
}