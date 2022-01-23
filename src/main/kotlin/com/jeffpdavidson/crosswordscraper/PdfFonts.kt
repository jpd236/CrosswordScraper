package com.jeffpdavidson.crosswordscraper

import browser.runtime.getURL
import com.jeffpdavidson.kotwords.formats.PdfFont
import com.jeffpdavidson.kotwords.formats.PdfFontFamily
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object PdfFonts {
    private val initMutex = Mutex()
    private var NOTO_SERIF_FONT_FAMILY: PdfFontFamily? = null
    private var NOTO_SANS_FONT_FAMILY: PdfFontFamily? = null

    suspend fun getNotoSerifFontFamily(): PdfFontFamily {
        initMutex.withLock {
            if (NOTO_SERIF_FONT_FAMILY == null) {
                NOTO_SERIF_FONT_FAMILY = loadFontFamily("NotoSerif")
            }
        }
        return NOTO_SERIF_FONT_FAMILY!!
    }

    suspend fun getNotoSansFontFamily(): PdfFontFamily {
        initMutex.withLock {
            if (NOTO_SANS_FONT_FAMILY == null) {
                NOTO_SANS_FONT_FAMILY = loadFontFamily("NotoSans")
            }
        }
        return NOTO_SANS_FONT_FAMILY!!
    }

    private suspend fun loadFontFamily(name: String): PdfFontFamily {
        return PdfFontFamily(
            PdfFont.TtfFont(name, "normal", Http.fetchAsBinary(getURL("fonts/$name-Regular.ttf"))),
            PdfFont.TtfFont(name, "bold", Http.fetchAsBinary(getURL("fonts/$name-Bold.ttf"))),
            PdfFont.TtfFont(name, "italic", Http.fetchAsBinary(getURL("fonts/$name-Italic.ttf"))),
            PdfFont.TtfFont(name, "bolditalic", Http.fetchAsBinary(getURL("fonts/$name-BoldItalic.ttf"))),
        )
    }
}