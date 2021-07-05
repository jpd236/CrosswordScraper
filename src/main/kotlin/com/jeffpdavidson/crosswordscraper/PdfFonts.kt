package com.jeffpdavidson.crosswordscraper

import browser.runtime.getURL
import com.jeffpdavidson.kotwords.formats.PdfFont
import com.jeffpdavidson.kotwords.formats.PdfFontFamily
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object PdfFonts {
    private val initMutex = Mutex()
    private var NOTO_FONT_FAMILY: PdfFontFamily? = null

    suspend fun getNotoFontFamily(): PdfFontFamily {
        initMutex.withLock {
            if (NOTO_FONT_FAMILY == null) {
                NOTO_FONT_FAMILY = PdfFontFamily(
                    PdfFont.TtfFont("NotoSerif", "normal", Http.fetchAsBinary(getURL("fonts/NotoSerif-Regular.ttf"))),
                    PdfFont.TtfFont("NotoSerif", "bold", Http.fetchAsBinary(getURL("fonts/NotoSerif-Bold.ttf"))),
                    PdfFont.TtfFont("NotoSerif", "italic", Http.fetchAsBinary(getURL("fonts/NotoSerif-Italic.ttf"))),
                    PdfFont.TtfFont("NotoSerif", "bolditalic",
                        Http.fetchAsBinary(getURL("fonts/NotoSerif-BoldItalic.ttf"))),
                )
            }
        }
        return NOTO_FONT_FAMILY!!
    }
}