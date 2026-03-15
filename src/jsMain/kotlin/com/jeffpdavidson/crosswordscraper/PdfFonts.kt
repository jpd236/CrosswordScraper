package com.jeffpdavidson.crosswordscraper

import browser.runtime.getURL
import com.jeffpdavidson.kotwords.formats.pdf.PdfFont
import com.jeffpdavidson.kotwords.formats.pdf.PdfFontFamily
import com.jeffpdavidson.kotwords.formats.pdf.PdfFontId

object PdfFonts {
    val NOTO_SERIF_FONT_FAMILY: PdfFontFamily = getFontFamily("NotoSerif")
    val NOTO_SANS_FONT_FAMILY: PdfFontFamily = getFontFamily("NotoSans")

    private fun getFontFamily(name: String): PdfFontFamily {
        return PdfFontFamily(
            baseFont = PdfFont.TtfFont(PdfFontId.TtfFontId("$name-Regular")) {
                Http.fetchAsBinary(getURL("fonts/$name-Regular.ttf"))
            },
            boldFont = PdfFont.TtfFont(PdfFontId.TtfFontId("$name-Bold")) {
                Http.fetchAsBinary(getURL("fonts/$name-Bold.ttf"))
            },
            italicFont = PdfFont.TtfFont(PdfFontId.TtfFontId("$name-Italic")) {
                Http.fetchAsBinary(getURL("fonts/$name-Italic.ttf"))
            },
            boldItalicFont = PdfFont.TtfFont(PdfFontId.TtfFontId("$name-BoldItalic")) {
                Http.fetchAsBinary(getURL("fonts/$name-BoldItalic.ttf"))
            }
        )
    }
}