package com.jeffpdavidson.crosswordscraper

import com.github.ajalt.colormath.model.RGB
import com.jeffpdavidson.kotwords.formats.Pdf
import kotlinx.browser.document
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.dom.addClass
import kotlinx.dom.removeClass
import kotlinx.html.InputType
import kotlinx.html.dom.append
import kotlinx.html.id
import kotlinx.html.js.button
import kotlinx.html.js.div
import kotlinx.html.js.h4
import kotlinx.html.js.input
import kotlinx.html.js.label
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onInputFunction
import kotlinx.html.js.option
import kotlinx.html.js.select
import kotlinx.html.js.span
import kotlinx.html.small
import kotlinx.html.style
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLSpanElement

/** Render the settings page and provide access to settings. */
object Settings {
    private const val ID_PUZ_UNICODE_SUPPORT = "puz-unicode"
    private const val ID_PDF_INK_SAVER_PERCENTAGE = "pdf-ink-saver"
    private const val ID_PDF_INK_SAVER_PERCENTAGE_TEXT = "pdf-ink-saver-text"
    private const val ID_PDF_INK_SAVER_PERCENTAGE_SQUARE = "pdf-ink-saver-square"
    private const val ID_PDF_FONT = "pdf-font"

    private val puzUnicodeSupportInput by lazy { document.getElementById(ID_PUZ_UNICODE_SUPPORT) as HTMLInputElement }
    private val pdfInkSaverPercentageInput by lazy {
        document.getElementById(ID_PDF_INK_SAVER_PERCENTAGE) as HTMLInputElement
    }
    private val pdfInkSaverPercentageText by lazy {
        document.getElementById(ID_PDF_INK_SAVER_PERCENTAGE_TEXT) as HTMLSpanElement
    }
    private val pdfInkSaverPercentageSquare by lazy {
        document.getElementById(ID_PDF_INK_SAVER_PERCENTAGE_SQUARE) as HTMLDivElement
    }
    private val pdfFont by lazy { document.getElementById(ID_PDF_FONT) as HTMLSelectElement }

    /** Render the popup page. Intended to be loaded from options.html. */
    suspend fun load() {
        val optionsContainer = document.getElementById("options-container") as HTMLDivElement
        optionsContainer.append {
            h4 {
                +"PUZ"
            }
            div("form-group") {
                div("form-check") {
                    input(type = InputType.checkBox, classes = "form-check-input") {
                        id = ID_PUZ_UNICODE_SUPPORT
                        onChangeFunction = {
                            setPuzUnicodeSupportEnabled(puzUnicodeSupportInput.checked)
                        }
                    }
                    label(classes = "form-check-label") {
                        htmlFor = ID_PUZ_UNICODE_SUPPORT
                        +"Unicode support"
                    }
                }
                small("form-text text-muted") {
                    +("Use a newer version of the .puz format which supports more characters in clues and metadata " +
                            "(when needed). Files may not work with all applications.")
                }
            }
            h4 {
                +"PDF"
            }
            div("form-group") {
                label {
                    htmlFor = ID_PDF_INK_SAVER_PERCENTAGE
                    +"Ink Saver Percentage"
                }
                div("d-flex align-items-center") {
                    input(type = InputType.range, classes = "custom-range") {
                        id = ID_PDF_INK_SAVER_PERCENTAGE
                        style = "max-width: 300px;"
                        onInputFunction = {
                            onInkSaverPercentageInput()
                        }
                        onChangeFunction = {
                            setPdfInkSaverPercentage(pdfInkSaverPercentageInput.value.toInt())
                        }
                    }
                    div("ml-2 border d-block") {
                        id = ID_PDF_INK_SAVER_PERCENTAGE_SQUARE
                        style = "width: 1.5em; height: 1.5em; border-color: black !important;"
                    }
                    span("ml-2") {
                        id = ID_PDF_INK_SAVER_PERCENTAGE_TEXT
                    }
                }
                small("form-text text-muted") {
                    +"Percentage to lighten black squares. 0% is pure black; 100% is pure white."
                }
            }
            div("form-group") {
                label {
                    htmlFor = ID_PDF_FONT
                    +"Font"
                }
                select("form-control") {
                    id = ID_PDF_FONT
                    style = "max-width: 300px;"
                    option {
                        value = "NotoSans"
                        +"Noto Sans"
                    }
                    option {
                        value = "NotoSerif"
                        +"Noto Serif"
                    }
                    onChangeFunction = {
                        setPdfFont(pdfFont.value)
                    }
                }

            }
            button(classes = "btn btn-secondary btn-sm") {
                +"Reset to defaults"
                onClickFunction = {
                    GlobalScope.launch {
                        resetDefaults()
                    }
                }
            }
        }

        initializeWidgets()

        val loadingContainer = document.getElementById("loading-container") as HTMLDivElement
        loadingContainer.addClass("d-none")
        optionsContainer.removeClass("d-none")
    }

    private suspend fun initializeWidgets() {
        puzUnicodeSupportInput.checked = isPuzUnicodeSupportEnabled()
        pdfInkSaverPercentageInput.value = getPdfInkSaverPercentage().toString()
        onInkSaverPercentageInput()
        pdfFont.value = getPdfFont()
    }

    private fun onInkSaverPercentageInput() {
        pdfInkSaverPercentageText.innerText = "${pdfInkSaverPercentageInput.value}%"
        pdfInkSaverPercentageSquare.style.backgroundColor =
            Pdf.getAdjustedColor(RGB("#000000"), pdfInkSaverPercentageInput.value.toInt() / 100f).toHex()
    }

    /** Whether the user has enabled unicode support for .puz files. Default is false. */
    suspend fun isPuzUnicodeSupportEnabled(): Boolean {
        val items = browser.storage.sync.get(ID_PUZ_UNICODE_SUPPORT).await()
        return items[ID_PUZ_UNICODE_SUPPORT] as? Boolean ?: false
    }

    private fun setPuzUnicodeSupportEnabled(enabled: Boolean) {
        val items = js("{}")
        items[ID_PUZ_UNICODE_SUPPORT] = enabled
        browser.storage.sync.set(items)
    }

    /** Lightness adjustment to be applied to black squares for .pdf files, from 0-100. Default is 0. */
    suspend fun getPdfInkSaverPercentage(): Int {
        val items = browser.storage.sync.get(ID_PDF_INK_SAVER_PERCENTAGE).await()
        return items[ID_PDF_INK_SAVER_PERCENTAGE] as? Int ?: 0
    }

    private fun setPdfInkSaverPercentage(percentage: Int) {
        val items = js("{}")
        items[ID_PDF_INK_SAVER_PERCENTAGE] = percentage
        browser.storage.sync.set(items)
    }

    suspend fun getPdfFont(): String {
        val items = browser.storage.sync.get(ID_PDF_FONT).await()
        return items[ID_PDF_FONT] as? String ?: "NotoSerif"
    }

    private fun setPdfFont(font: String) {
        val items = js("{}")
        items[ID_PDF_FONT] = font
        browser.storage.sync.set(items)
    }

    private suspend fun resetDefaults() {
        browser.storage.sync.clear().await()
        initializeWidgets()
    }
}