package com.jeffpdavidson.crosswordscraper

import browser.permissions.Permissions
import browser.runtime.getURL
import com.jeffpdavidson.crosswordscraper.sources.AmuseLabsSource
import com.jeffpdavidson.crosswordscraper.sources.BostonGlobeSource
import com.jeffpdavidson.crosswordscraper.sources.CnnSource
import com.jeffpdavidson.crosswordscraper.sources.CrosshareSource
import com.jeffpdavidson.crosswordscraper.sources.CrosswordCompilerSource
import com.jeffpdavidson.crosswordscraper.sources.CrosswordNexusSource
import com.jeffpdavidson.crosswordscraper.sources.CrosswordrSource
import com.jeffpdavidson.crosswordscraper.sources.DailyPrincetonianSource
import com.jeffpdavidson.crosswordscraper.sources.GoComicsSource
import com.jeffpdavidson.crosswordscraper.sources.GuardianSource
import com.jeffpdavidson.crosswordscraper.sources.NewYorkTimesSource
import com.jeffpdavidson.crosswordscraper.sources.NewYorkerSource
import com.jeffpdavidson.crosswordscraper.sources.PuzzleLinkSource
import com.jeffpdavidson.crosswordscraper.sources.PzzlSource
import com.jeffpdavidson.crosswordscraper.sources.ScrapeResult
import com.jeffpdavidson.crosswordscraper.sources.TheWeekSource
import com.jeffpdavidson.crosswordscraper.sources.UniversalSource
import com.jeffpdavidson.crosswordscraper.sources.WallStreetJournalSource
import com.jeffpdavidson.crosswordscraper.sources.WashingtonPostSource
import com.jeffpdavidson.crosswordscraper.sources.WorldOfCrosswordsSource
import com.jeffpdavidson.crosswordscraper.sources.XWordInfoSource
import com.jeffpdavidson.kotwords.formats.Puzzleable
import com.jeffpdavidson.kotwords.model.Puzzle
import isFirefoxForAndroid
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.dom.addClass
import kotlinx.dom.clear
import kotlinx.dom.removeClass
import kotlinx.html.ButtonType
import kotlinx.html.HtmlBlockTag
import kotlinx.html.a
import kotlinx.html.br
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.dom.append
import kotlinx.html.id
import kotlinx.html.js.button
import kotlinx.html.js.hr
import kotlinx.html.js.li
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.p
import kotlinx.html.js.ul
import kotlinx.html.style
import kotlinx.html.tabIndex
import kotlinx.html.unsafe
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLParagraphElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.FileReader
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Date

/** Core logic of the scraper - render the popup page and perform scraping. */
object CrosswordScraper {
    /**
     * Ordered list of sources.
     *
     * If a duplicate grid (per [isDuplicate]) is found, the earlier source in the list is preferred.
     */
    private val SOURCES = listOf(
        AmuseLabsSource,
        BostonGlobeSource,
        CnnSource,
        CrosshareSource,
        CrosswordCompilerSource,
        CrosswordNexusSource,
        CrosswordrSource,
        DailyPrincetonianSource,
        GoComicsSource,
        GuardianSource,
        NewYorkTimesSource,
        NewYorkerSource,
        PzzlSource,
        TheWeekSource,
        UniversalSource,
        WallStreetJournalSource,
        WashingtonPostSource,
        WorldOfCrosswordsSource,
        XWordInfoSource,

        // Prefer extracted puzzles from applets to .puz files, which tend to be more constrained.
        PuzzleLinkSource,
    )

    private val mainScope = MainScope()

    /** Render the popup page. Intended to be loaded from popup.html. */
    suspend fun load() {
        val (puzzles, debugLog) = scrapePuzzles()

        // Always render the popup, even if automatic download is enabled. If there's an error with the download, this
        // allows the user to pick another option or report an issue.
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
                                is ProcessedScrapeResult.Success -> renderScrapeSuccess(scrapedPuzzle)
                                is ProcessedScrapeResult.Error -> renderScrapeError(scrapedPuzzle)
                                is ProcessedScrapeResult.NeedPermissions -> {
                                    renderPermissionPrompt(scrapedPuzzle) { granted ->
                                        // In practice, the popup seems to be closed after a permission prompt, but
                                        // in case that changes, retry the scrape if the permission was granted.
                                        // See https://bugs.chromium.org/p/chromium/issues/detail?id=952645.
                                        if (granted) {
                                            puzzleContainer.clear()
                                            mainScope.launch {
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
            if (isFirefoxForAndroid()) {
                // Open the options page as a regular link on Firefox for Android. Otherwise, it is opened under the
                // popup, so no one would notice it is there until the popup is closed:
                // https://bugzilla.mozilla.org/show_bug.cgi?id=1884550
                a(classes = "btn btn-secondary btn-sm mt-3") {
                    +"Settings"
                    href = getURL("options.html")
                    target = "_blank"
                }
            } else {
                button(classes = "btn btn-secondary btn-sm mt-3") {
                    +"Settings"
                    onClickFunction = {
                        browser.runtime.openOptionsPage()
                    }
                }
            }

            hr { }
            div(classes = "text-muted") {
                style = "font-size: 0.7rem"
                +"Version: ${browser.runtime.getManifest().version}"
                br { }
                a {
                    href = "https://github.com/jpd236/CrosswordScraper/issues/new"
                    target = "_blank"
                    +"Report issue"
                }
                +" | "
                a {
                    href = "#"
                    onClickFunction = {
                        mainScope.launch {
                            startDownload(
                                "CrosswordScraper-debug-log-${Date(Date.now()).toISOString()}.txt",
                                debugLog.encodeToByteArray()
                            )
                        }
                    }
                    +"Save debug log"
                }
            }
        }

        if (Settings.isAutoDownloadEnabled() && puzzles.size == 1 && puzzles.first() is ProcessedScrapeResult.Success) {
            // Attempt to automatically download the puzzle, and close the window if it succeeds.
            val scrapedPuzzle = puzzles.first() as ProcessedScrapeResult.Success
            val baseFilename = getBaseFilename(scrapedPuzzle)
            mainScope.launch {
                if (onDownloadClicked(baseFilename, Settings.getAutoDownloadFormat(), scrapedPuzzle)) {
                    // Close the window automatically. HACK: Some delay seems to be necessary for Firefox to start the
                    // download successfully; it's not clear what other event this could be tied to.
                    delay(100)
                    window.close()
                } else {
                    onLoadingComplete()
                }
            }
        } else {
            onLoadingComplete()
        }
    }

    private fun onLoadingComplete() {
        val loadingContainer = document.getElementById("loading-container") as HTMLDivElement
        loadingContainer.addClass("d-none")

        val puzzleContainer = document.getElementById("puzzle-container") as HTMLDivElement
        puzzleContainer.removeClass("d-none")
    }

    private sealed class ProcessedScrapeResult {
        data class Success(val source: String, val puzzleable: Puzzleable, val puzzle: Puzzle) : ProcessedScrapeResult()
        data class NeedPermissions(
            val source: String,
            val permissions: List<String>,
            val prompt: String,
        ) : ProcessedScrapeResult()

        data class Error(val source: String) : ProcessedScrapeResult()
    }

    /** Scrape puzzles from all frames in the user's active tab. */
    private suspend fun scrapePuzzles(): Pair<Set<ProcessedScrapeResult>, String> {
        val debugLog = StringBuilder()
        debugLog.appendLine("Crossword Scraper Debug Log")
        debugLog.appendLine("Please attach this file to any issue report")
        debugLog.appendLine("-------------------------------------------")
        debugLog.appendLine("Generated at: ${Date(Date.now()).toISOString()}")
        debugLog.appendLine("Extension version: ${browser.runtime.getManifest().version}")
        debugLog.appendLine("Browser: ${window.navigator.userAgent}")

        val (tabId, frames) = Scraping.getAllFrames()
        debugLog.appendLine("URL: ${frames.firstOrNull { it.parentFrameId == -1 }?.url ?: "<unknown>"}")

        val puzzles = mutableSetOf<ProcessedScrapeResult>()
        val processedGrids = mutableListOf<List<List<Puzzle.Cell>>>()
        debugLog.appendLine("Scraped Puzzles:")
        frames.forEach { frame ->
            val isTopLevel = frame.parentFrameId == -1
            SOURCES.forEach { source ->
                val url = URL(frame.url)
                if (source.matchesUrl(url)) {
                    try {
                        when (val result = source.scrapePuzzles(url, tabId, frame.frameId, isTopLevel)) {
                            is ScrapeResult.Success -> {
                                result.puzzles.forEach { puzzleable ->
                                    try {
                                        // Even though the ultimate conversion uses the Puzzleable - to avoid touching a
                                        // file that's already in the desired format - we still need to parse as a
                                        // Puzzle so we know the metadata to show in the list and to be able to show
                                        // other supported formats.
                                        val puzzle = puzzleable.asPuzzle()
                                        if (!isDuplicate(processedGrids, puzzle.grid)) {
                                            puzzles.add(
                                                ProcessedScrapeResult.Success(source.sourceName, puzzleable, puzzle)
                                            )
                                            processedGrids.add(puzzle.grid)
                                            debugLog.appendLine(
                                                "Successful scrape: source = ${source.sourceName}, " +
                                                        "puzzle title = ${puzzle.title}"
                                            )
                                        } else {
                                            debugLog.appendLine(
                                                "Duplicate grid: source = ${source.sourceName}, " +
                                                        "puzzle title = ${puzzle.title}"
                                            )
                                        }
                                    } catch (t: Throwable) {
                                        console.error(
                                            "Error converting ${source.sourceName} data to crossword:",
                                            t.stackTraceToString()
                                        )
                                        puzzles.add(ProcessedScrapeResult.Error(source.sourceName))
                                        debugLog.appendLine("Scrape exception: source = ${source.sourceName}")
                                        debugLog.appendLine("-------")
                                        debugLog.append(t.stackTraceToString())
                                        debugLog.appendLine("-------")
                                    }
                                }
                            }
                            is ScrapeResult.NeedPermissions ->
                                // Only add permission prompts if none of the previous results contain the same set of
                                // permission requests.
                                if (puzzles.none { processedResult ->
                                        processedResult is ProcessedScrapeResult.NeedPermissions
                                                && processedResult.permissions.toSet() == result.permissions.toSet()
                                    }
                                ) {
                                    puzzles.add(
                                        ProcessedScrapeResult.NeedPermissions(
                                            source.sourceName,
                                            result.permissions,
                                            result.prompt,
                                        )
                                    )
                                    debugLog.appendLine(
                                        "Need permission: source = ${source.sourceName}, " +
                                                "permissions = ${result.permissions}, prompt = ${result.prompt}"
                                    )
                                } else {
                                    debugLog.appendLine(
                                        "Need permissions: source = ${source.sourceName}, already covered"
                                    )
                                }
                            is ScrapeResult.Error -> {
                                puzzles.add(ProcessedScrapeResult.Error(source.sourceName))
                                debugLog.appendLine(
                                    "Scrape error: source = ${source.sourceName}, error = ${result.debugMsg}"
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        console.error("Error scraping puzzles for source ${source.sourceName}", t.stackTraceToString())
                        puzzles.add(ProcessedScrapeResult.Error(source.sourceName))
                        debugLog.appendLine("Source scrape error, source = ${source.sourceName}")
                        debugLog.appendLine("-------")
                        debugLog.append(t.stackTraceToString())
                        debugLog.appendLine("-------")
                    }
                }
            }
        }
        return puzzles to debugLog.toString()
    }

    private fun HtmlBlockTag.renderScrapeSuccess(scrapedPuzzle: ProcessedScrapeResult.Success) {
        // Title to display in the UI - in descending priority, title, then author, then scraping source.
        val puzzleTitle = scrapedPuzzle.puzzle.title
        val puzzleAuthor = scrapedPuzzle.puzzle.creator
        val source = scrapedPuzzle.source
        val title = puzzleTitle.ifEmpty { puzzleAuthor.ifEmpty { source } }
        div(classes = "mb-1") {
            if (scrapedPuzzle.puzzle.hasHtmlClues) {
                unsafe {
                    +title
                }
            } else {
                +title
            }
        }

        val baseFilename = getBaseFilename(scrapedPuzzle)

        // Render each download link labeled by the extension, separated by "|".
        FileFormat.values()
            .filter { fileFormat -> fileFormat.supportsPuzzle(scrapedPuzzle.puzzle) }
            .forEachIndexed { i, fileFormat ->
                if (i > 0) {
                    +" | "
                }
                a {
                    href = "#"
                    onClickFunction = {
                        mainScope.launch {
                            onDownloadClicked(baseFilename, fileFormat, scrapedPuzzle)
                        }
                    }
                    +fileFormat.extension.uppercase()
                }
            }
    }

    /** Return the base filename (without extension) to use when downloading this puzzle. */
    private fun getBaseFilename(scrapedPuzzle: ProcessedScrapeResult.Success): String {
        // In descending priority, author-title, title, author, scraping source.
        // Each word is capitalized, and non-alphanumeric characters are removed.
        val puzzleTitle = scrapedPuzzle.puzzle.title
        val puzzleAuthor = scrapedPuzzle.puzzle.creator
        val source = scrapedPuzzle.source
        val fileTitleParts = listOf(puzzleAuthor, puzzleTitle).filterNot { it.isEmpty() }
        return if (fileTitleParts.isEmpty()) {
            source
        } else {
            fileTitleParts.joinToString("-") { part ->
                val cleanedText = if (scrapedPuzzle.puzzle.hasHtmlClues) {
                    val elem = document.createElement("div") as HTMLDivElement
                    elem.innerHTML = part
                    elem.innerText
                } else {
                    part
                }
                cleanedText.split("\\s+".toRegex()).joinToString("") {
                    it.replace("[^A-Za-z0-9]".toRegex(), "").replaceFirstChar { ch -> ch.uppercase() }
                }
            }
        }
    }

    /**
     * Start the download, or show an error dialog if the download fails.
     *
     * @return whether the download was started successfully.
     */
    private suspend fun onDownloadClicked(
        baseFilename: String,
        fileFormat: FileFormat,
        scrapedPuzzle: ProcessedScrapeResult.Success
    ): Boolean {
        return try {
            startDownload(
                "$baseFilename.${fileFormat.extension}",
                fileFormat.puzzleableToBinary(scrapedPuzzle.puzzleable)
            )
            true
        } catch (t: Throwable) {
            console.info("Error converting to ${fileFormat.name}:", t.stackTraceToString())
            showErrorModal(
                "Error converting to ${fileFormat.extension.uppercase()}. " +
                        "Please try another format."
            )
            false
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
            +scrapedPuzzle.prompt
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
                                    attributes["data-bs-dismiss"] = "modal"
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
        js("new bootstrap.Modal('#error-dialog').show()")
    }

    /** Initiate a user download of the given data. */
    private suspend fun startDownload(fileName: String, data: ByteArray) {
        val reader = FileReader()
        suspendCoroutine { cont ->
            reader.onload = { event ->
                val link = document.createElement("a") as HTMLAnchorElement
                link.download = fileName
                link.href = event.target.asDynamic().result as String
                link.click()
                cont.resume(Unit)
            }
            reader.readAsDataURL(Blob(arrayOf(Int8Array(data.toTypedArray()).buffer)))
        }
    }

    /**
     * Whether the given [grid] is likely a duplicate of a grid in [processedGrids].
     *
     * Since a page may have two different sources for the same puzzle - e.g. a .puz link and an AmuseLabs applet - we
     * can't rely on exact comparisons as different sources have different features. As a simple spot check, we see
     * whether the solution characters are identical for at least 60% of the grid.
     */
    private fun isDuplicate(processedGrids: List<List<List<Puzzle.Cell>>>, grid: List<List<Puzzle.Cell>>): Boolean {
        return processedGrids.any { processedGrid ->
            if (processedGrid.size != grid.size || processedGrid[0].size != grid[0].size) {
                false
            } else {
                var identicalSquares = 0
                var nonBlackOrDifferentSquares = 0
                grid.forEachIndexed { y, row ->
                    row.forEachIndexed { x, cell ->
                        if (!cell.cellType.isBlack() && cell.solution == processedGrid[y][x].solution) {
                            identicalSquares++
                        }
                        if (!cell.cellType.isBlack() || !processedGrid[y][x].cellType.isBlack()) {
                            nonBlackOrDifferentSquares++
                        }
                    }
                }
                identicalSquares.toDouble() / nonBlackOrDifferentSquares > 0.6
            }
        }
    }
}