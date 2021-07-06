package com.jeffpdavidson.crosswordscraper

import kotlinx.browser.document
import kotlinx.browser.window

fun main() {
    document.onContentLoadedEventAsync {
        when (window.location.pathname) {
            "/popup.html" -> CrosswordScraper.load()
            "/options.html" -> Settings.load()
            else -> console.error("Unknown location: ${window.location}")
        }
    }
}
