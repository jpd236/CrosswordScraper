# Crossword Scraper
[![GitHub Workflow Status](https://img.shields.io/github/workflow/status/jpd236/CrosswordScraper/Gradle)](https://github.com/jpd236/CrosswordScraper/actions/workflows/gradle-build.yaml)

Browser extension which downloads crosswords from crossword applets for offline solving.

## Install
- [Mozilla Firefox](https://addons.mozilla.org/en-US/firefox/addon/crossword-scraper/)
- [Google Chrome](https://chrome.google.com/webstore/detail/crossword-scraper/lmneijnoafbpnfdjabialjehgohpmcpo)

## Development
Crossword Scraper is built on Kotlin/JS. To build and run the extension locally, run:

`./gradlew developmentV2Extension`

then install the extension from the `build/extension/developmentV2` directory.

Replace "V2" with "V3" for Manifest v3 support.

Most of the actual puzzle format conversion uses the [kotwords](https://github.com/jpd236/kotwords) library.
