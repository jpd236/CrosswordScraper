package com.jeffpdavidson.crosswordscraper

import com.jeffpdavidson.kotwords.formats.AcrossLite.Companion.toAcrossLiteBinary
import com.jeffpdavidson.kotwords.formats.Jpz.Companion.toJpz
import com.jeffpdavidson.kotwords.formats.Pdf.asPdf
import com.jeffpdavidson.kotwords.model.Crossword

/** Supported output file formats. */
enum class FileFormat(val extension: String, val toBinary: suspend (Crossword) -> ByteArray) {
    ACROSS_LITE("puz", { it.toAcrossLiteBinary(writeUtf8 = Settings.isPuzUnicodeSupportEnabled()) }),
    JPZ("jpz", { it.toJpz().toCompressedFile("puzzle.jpz") }),
    PDF("pdf", {
        it.asPdf(
            fontFamily = PdfFonts.getNotoFontFamily(),
            blackSquareLightnessAdjustment = Settings.getPdfInkSaverPercentage() / 100f
        )
    }),
}