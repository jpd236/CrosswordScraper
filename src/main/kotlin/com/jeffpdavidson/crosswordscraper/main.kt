package com.jeffpdavidson.crosswordscraper

import browser.permissions.Permissions
import com.jeffpdavidson.crosswordscraper.sources.AmuseLabsSource
import com.jeffpdavidson.crosswordscraper.sources.BostonGlobeSource
import com.jeffpdavidson.crosswordscraper.sources.CrosshareSource
import com.jeffpdavidson.crosswordscraper.sources.PuzzleLinkSource
import com.jeffpdavidson.crosswordscraper.sources.ScrapeResult
import com.jeffpdavidson.crosswordscraper.sources.UniversalSource
import com.jeffpdavidson.crosswordscraper.sources.WallStreetJournalSource
import com.jeffpdavidson.crosswordscraper.sources.WorldOfCrosswordsSource
import com.jeffpdavidson.kotwords.model.Crossword
import kotlinx.browser.document
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.dom.addClass
import kotlinx.dom.clear
import kotlinx.dom.removeClass
import kotlinx.html.ButtonType
import kotlinx.html.HtmlBlockTag
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.dom.append
import kotlinx.html.id
import kotlinx.html.js.button
import kotlinx.html.js.li
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.p
import kotlinx.html.js.ul
import kotlinx.html.tabIndex
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLParagraphElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.FileReader

fun main() {
    document.onContentLoadedEventAsync {
        launchCrosswordScraper()
    }
}

private val SOURCES = listOf(
    AmuseLabsSource,
    BostonGlobeSource,
    CrosshareSource,
    PuzzleLinkSource,
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
                            is ProcessedScrapeResult.Success -> renderScrapeSuccess(scrapedPuzzle)
                            is ProcessedScrapeResult.Error -> renderScrapeError(scrapedPuzzle)
                            is ProcessedScrapeResult.NeedPermissions -> {
                                renderPermissionPrompt(scrapedPuzzle) { granted ->
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
    }

    val loadingContainer = document.getElementById("loading-container") as HTMLDivElement
    loadingContainer.addClass("d-none")
    puzzleContainer.removeClass("d-none")
}

private sealed class ProcessedScrapeResult {
    data class Success(val source: String, val crossword: Crossword) : ProcessedScrapeResult()
    data class NeedPermissions(val source: String, val permissions: List<String>) : ProcessedScrapeResult()
    data class Error(val source: String) : ProcessedScrapeResult()
}

/** Scrape puzzles from all frames in the user's active tab. */
private suspend fun scrapePuzzles(): Set<ProcessedScrapeResult> {
    val frames = Scraping.getAllFrames()
    val puzzles = mutableSetOf<ProcessedScrapeResult>()
    val processedCrosswords = mutableListOf<Crossword>()
    frames.forEach { frame ->
        val isTopLevel = frame.parentFrameId == -1
        SOURCES.forEach { source ->
            val url = URL(frame.url)
            if (source.matchesUrl(url)) {
                try {
                    when (val result = source.scrapePuzzles(url, frame.frameId, isTopLevel)) {
                        is ScrapeResult.Success -> {
                            result.crosswords.forEach { crosswordable ->
                                try {
                                    val crossword = crosswordable.asCrossword()
                                    if (!isDuplicate(processedCrosswords, crossword)) {
                                        puzzles.add(ProcessedScrapeResult.Success(source.sourceName, crossword))
                                        processedCrosswords.add(crossword)
                                    }
                                } catch (t: Throwable) {
                                    console.error(
                                        "Error converting ${source.sourceName} data to crossword:",
                                        t.stackTraceToString()
                                    )
                                    puzzles.add(ProcessedScrapeResult.Error(source.sourceName))
                                }
                            }
                        }
                        is ScrapeResult.NeedPermissions ->
                            puzzles.add(ProcessedScrapeResult.NeedPermissions(source.sourceName, result.permissions))
                        is ScrapeResult.Error ->
                            puzzles.add(ProcessedScrapeResult.Error(source.sourceName))
                    }
                } catch (t: Throwable) {
                    console.error("Error scraping puzzles for source ${source.sourceName}", t.stackTraceToString())
                    puzzles.add(ProcessedScrapeResult.Error(source.sourceName))
                }
            }
        }
    }
    return puzzles
}

private fun HtmlBlockTag.renderScrapeSuccess(scrapedPuzzle: ProcessedScrapeResult.Success) {
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
    FileFormat.values().forEachIndexed { i, fileFormat ->
        if (i > 0) {
            +" | "
        }
        a {
            href = "#"
            onClickFunction = {
                GlobalScope.launch {
                    try {
                        startDownload(
                            "$filenameBase.${fileFormat.extension}",
                            fileFormat.toBinary(scrapedPuzzle.crossword)
                        )
                    } catch (t: Throwable) {
                        console.info("Error converting to ${fileFormat.name}:", t.stackTraceToString())
                        showErrorModal(
                            "Error converting to ${fileFormat.extension.uppercase()}. Please try another format."
                        )
                    }
                }
            }
            +fileFormat.extension.uppercase()
        }
    }
}

private fun HtmlBlockTag.renderScrapeError(scrapedPuzzle: ProcessedScrapeResult.Error) {
    classes = classes + "disabled"
    div(classes = "mb-1") {
        +scrapedPuzzle.source
    }
    +"Scrape error"
}

private fun HtmlBlockTag.renderPermissionPrompt(
    scrapedPuzzle: ProcessedScrapeResult.NeedPermissions,
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

/** Show an error modal (dialog) with the given message. */
private fun showErrorModal(message: String) {
    val errorDialog = document.getElementById("error-dialog")
    if (errorDialog == null) {
        document.getElementById("root")!!.append {
            div("modal") {
                id = "error-dialog"
                tabIndex = "-1"
                div("modal-dialog modal-sm") {
                    div("modal-content") {
                        div("modal-body") {
                            p {
                                id = "error-message"
                                +message
                            }
                        }
                        div("modal-footer") {
                            button(type = ButtonType.button, classes = "btn btn-secondary") {
                                attributes["data-dismiss"] = "modal"
                                +"Close"
                            }
                        }
                    }
                }
            }
        }
    } else {
        val errorMessageElement = document.getElementById("error-message") as HTMLParagraphElement
        errorMessageElement.innerText = message
    }
    val modal = js("$('#error-dialog')")
    modal.modal("show")
}

/** Initiate a user download of the given data. */
private fun startDownload(fileName: String, data: ByteArray) {
    val reader = FileReader()
    reader.onload = { event ->
        val link = document.createElement("a") as HTMLAnchorElement
        link.download = fileName
        link.href = event.target.asDynamic().result as String
        link.click()
    }
    reader.readAsDataURL(Blob(arrayOf(Int8Array(data.toTypedArray()).buffer)))
}

/**
 * Whether the given [crossword] is likely a duplicate of a crossword in [processedCrosswords].
 *
 * Since a page may have two different sources for the same puzzle - e.g. a .puz link and an AmuseLabs applet - we can't
 * rely on exact comparisons as different sources have different features. As a simple spot check, we see whether the
 * solution characters are identical for at least 60% of the grid.
 */
private fun isDuplicate(processedCrosswords: List<Crossword>, crossword: Crossword): Boolean {
    return processedCrosswords.any { processedCrossword ->
        if (processedCrossword.grid.size != crossword.grid.size ||
            processedCrossword.grid[0].size != crossword.grid[0].size
        ) {
            false
        } else {
            var identicalSquares = 0
            var nonBlackOrDifferentSquares = 0
            crossword.grid.forEachIndexed { y, row ->
                row.forEachIndexed { x, square ->
                    if (!square.isBlack && square.solution == processedCrossword.grid[y][x].solution) {
                        identicalSquares++
                    }
                    if (!square.isBlack || square.isBlack != processedCrossword.grid[y][x].isBlack) {
                        nonBlackOrDifferentSquares++
                    }
                }
            }
            identicalSquares.toDouble() / nonBlackOrDifferentSquares > 0.6
        }
    }
}