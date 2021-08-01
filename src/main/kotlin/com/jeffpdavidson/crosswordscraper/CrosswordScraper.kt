package com.jeffpdavidson.crosswordscraper

import browser.permissions.Permissions
import com.jeffpdavidson.crosswordscraper.sources.AmuseLabsSource
import com.jeffpdavidson.crosswordscraper.sources.BostonGlobeSource
import com.jeffpdavidson.crosswordscraper.sources.CrosshareSource
import com.jeffpdavidson.crosswordscraper.sources.CrosswordNexusSource
import com.jeffpdavidson.crosswordscraper.sources.PuzzleLinkSource
import com.jeffpdavidson.crosswordscraper.sources.ScrapeResult
import com.jeffpdavidson.crosswordscraper.sources.Source
import com.jeffpdavidson.crosswordscraper.sources.UniversalSource
import com.jeffpdavidson.crosswordscraper.sources.WallStreetJournalSource
import com.jeffpdavidson.crosswordscraper.sources.WorldOfCrosswordsSource
import com.jeffpdavidson.kotwords.model.Crossword
import com.jeffpdavidson.kotwords.model.Puzzle
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

/** Core logic of the scraper - render the popup page and perform scraping. */
object CrosswordScraper {
    private val SOURCES = listOf(
        AmuseLabsSource,
        BostonGlobeSource,
        CrosshareSource,
        CrosswordNexusSource,
        PuzzleLinkSource,
        UniversalSource,
        WallStreetJournalSource,
        WorldOfCrosswordsSource,
    )

    /** Render the popup page. Intended to be loaded from popup.html. */
    suspend fun load() {
        val puzzles = scrapePuzzles()

        val puzzleContainer = document.getElementById("puzzle-container") as HTMLDivElement
        puzzleContainer.append {
            if (puzzles.isEmpty()) {
                p("mb-0") {
                    +"No puzzles found!"
                }
            } else {
                ul(classes = "list-group") {
                    puzzles.forEach { scrapedPuzzle ->
                        li(classes = "list-group-item") {
                            when (scrapedPuzzle) {
                                is ProcessedScrapeResult.Successful -> renderScrapeSuccess(scrapedPuzzle)
                                is ProcessedScrapeResult.Error -> renderScrapeError(scrapedPuzzle)
                                is ProcessedScrapeResult.NeedPermissions -> {
                                    renderPermissionPrompt(scrapedPuzzle) { granted ->
                                        // In practice, the popup seems to be closed after a permission prompt, but
                                        // in case that changes, retry the scrape if the permission was granted.
                                        // See https://bugs.chromium.org/p/chromium/issues/detail?id=952645.
                                        if (granted) {
                                            puzzleContainer.clear()
                                            GlobalScope.launch {
                                                load()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            button(classes = "btn btn-secondary btn-sm mt-3") {
                +"Settings"
                onClickFunction = {
                    browser.runtime.openOptionsPage()
                }
            }
        }

        val loadingContainer = document.getElementById("loading-container") as HTMLDivElement
        loadingContainer.addClass("d-none")
        puzzleContainer.removeClass("d-none")
    }

    private sealed class ProcessedScrapeResult {
        sealed class Successful : ProcessedScrapeResult()
        data class SuccessfulCrossword(val source: String, val crossword: Crossword) : Successful()
        data class SuccessfulPuzzle(val source: String, val puzzle: Puzzle) : Successful()
        data class NeedPermissions(val source: String, val permissions: List<String>) : ProcessedScrapeResult()
        data class Error(val source: String) : ProcessedScrapeResult()
    }

    /** Scrape puzzles from all frames in the user's active tab. */
    private suspend fun scrapePuzzles(): Set<ProcessedScrapeResult> {
        val frames = Scraping.getAllFrames()
        val puzzles = mutableSetOf<ProcessedScrapeResult>()
        val processedGrids = mutableListOf<List<List<Char?>>>()
        fun processSuccessfulScrape(
            source: Source,
            processResultFn: () -> Pair<ProcessedScrapeResult.Successful, List<List<Char?>>>
        ) {
            try {
                val (processedResult, grid) = processResultFn()
                if (!isDuplicate(processedGrids, grid)) {
                    puzzles.add(processedResult)
                    processedGrids.add(grid)
                }
            } catch (t: Throwable) {
                console.error(
                    "Error converting ${source.sourceName} data to crossword:",
                    t.stackTraceToString()
                )
                puzzles.add(ProcessedScrapeResult.Error(source.sourceName))
            }
        }
        frames.forEach { frame ->
            val isTopLevel = frame.parentFrameId == -1
            SOURCES.forEach { source ->
                val url = URL(frame.url)
                if (source.matchesUrl(url)) {
                    try {
                        when (val result = source.scrapePuzzles(url, frame.frameId, isTopLevel)) {
                            is ScrapeResult.Success -> {
                                result.crosswords.forEach { crosswordable ->
                                    processSuccessfulScrape(source) {
                                        val crossword = crosswordable.asCrossword()
                                        val grid = crossword.grid.map { row ->
                                            row.map { square ->
                                                square.solution
                                            }
                                        }
                                        ProcessedScrapeResult.SuccessfulCrossword(source.sourceName, crossword) to grid
                                    }
                                }
                                result.puzzles.forEach { puzzle ->
                                    processSuccessfulScrape(source) {
                                        val grid = puzzle.grid.map { row ->
                                            row.map { cell ->
                                                if (cell.solution.isNotEmpty()) cell.solution[0] else null
                                            }
                                        }
                                        ProcessedScrapeResult.SuccessfulPuzzle(source.sourceName, puzzle) to grid
                                    }
                                    puzzles.add(ProcessedScrapeResult.SuccessfulPuzzle(source.sourceName, puzzle))
                                }
                            }
                            is ScrapeResult.NeedPermissions ->
                                puzzles.add(
                                    ProcessedScrapeResult.NeedPermissions(source.sourceName, result.permissions)
                                )
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

    private fun HtmlBlockTag.renderScrapeSuccess(scrapedPuzzle: ProcessedScrapeResult.Successful) {
        // Title to display in the UI - in descending priority, title, then author, then scraping source.
        val puzzleTitle: String
        val puzzleAuthor: String
        val source: String
        when (scrapedPuzzle) {
            is ProcessedScrapeResult.SuccessfulCrossword -> {
                puzzleTitle = scrapedPuzzle.crossword.title
                puzzleAuthor = scrapedPuzzle.crossword.author
                source = scrapedPuzzle.source
            }
            is ProcessedScrapeResult.SuccessfulPuzzle -> {
                puzzleTitle = scrapedPuzzle.puzzle.title
                puzzleAuthor = scrapedPuzzle.puzzle.creator
                source = scrapedPuzzle.source
            }
        }
        val title = puzzleTitle.ifEmpty { puzzleAuthor.ifEmpty { source } }
        div(classes = "mb-1") {
            +title
        }

        // Filename to use with the output files - in descending priority, author-title, title, author, scraping source.
        // Each word is capitalized, and non-alphanumeric characters are removed.
        val fileTitleParts = listOf(puzzleAuthor, puzzleTitle).filterNot { it.isEmpty() }
        val filenameBase =
            if (fileTitleParts.isEmpty()) {
                source
            } else {
                fileTitleParts.joinToString("-") { part ->
                    part.split("\\s+".toRegex()).joinToString("") {
                        it.replace("[^A-Za-z0-9]".toRegex(), "").replaceFirstChar { ch -> ch.uppercase() }
                    }
                }
            }

        // Render each download link labeled by the extension, separated by "|".
        FileFormat.values()
            .filter { fileFormat ->
                scrapedPuzzle is ProcessedScrapeResult.SuccessfulCrossword || fileFormat.puzzleToBinary != null
            }
            .forEachIndexed { i, fileFormat ->
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
                                    when (scrapedPuzzle) {
                                        is ProcessedScrapeResult.SuccessfulCrossword ->
                                            fileFormat.crosswordToBinary(scrapedPuzzle.crossword)
                                        is ProcessedScrapeResult.SuccessfulPuzzle ->
                                            fileFormat.puzzleToBinary!!(scrapedPuzzle.puzzle)
                                    }
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
     * Whether the given [grid] is likely a duplicate of a grid in [processedGrids].
     *
     * Since a page may have two different sources for the same puzzle - e.g. a .puz link and an AmuseLabs applet - we
     * can't rely on exact comparisons as different sources have different features. As a simple spot check, we see
     * whether the solution characters are identical for at least 60% of the grid.
     */
    private fun isDuplicate(processedGrids: List<List<List<Char?>>>, grid: List<List<Char?>>): Boolean {
        return processedGrids.any { processedGrid ->
            if (processedGrid.size != grid.size || processedGrid[0].size != grid[0].size) {
                false
            } else {
                var identicalSquares = 0
                var nonBlackOrDifferentSquares = 0
                grid.forEachIndexed { y, row ->
                    row.forEachIndexed { x, square ->
                        if (square != null && square == processedGrid[y][x]) {
                            identicalSquares++
                        }
                        if (square != null || processedGrid[y][x] != null) {
                            nonBlackOrDifferentSquares++
                        }
                    }
                }
                identicalSquares.toDouble() / nonBlackOrDifferentSquares > 0.6
            }
        }
    }
}