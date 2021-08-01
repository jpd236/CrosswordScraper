package com.jeffpdavidson.crosswordscraper

import com.jeffpdavidson.kotwords.formats.AcrossLite.Companion.toAcrossLiteBinary
import com.jeffpdavidson.kotwords.formats.Jpz.Companion.toJpz
import com.jeffpdavidson.kotwords.formats.Pdf.asPdf
import com.jeffpdavidson.kotwords.model.Crossword
import com.jeffpdavidson.kotwords.model.Puzzle

/** Supported output file formats. */
enum class FileFormat(
    val extension: String,
    val crosswordToBinary: suspend (Crossword) -> ByteArray,
    val puzzleToBinary: (suspend (Puzzle) -> ByteArray)? = null,
) {
    ACROSS_LITE("puz", { it.toAcrossLiteBinary(writeUtf8 = Settings.isPuzUnicodeSupportEnabled()) }),
    JPZ("jpz", { it.toJpz().toCompressedFile("puzzle.jpz") }, { it.asJpzFile().toCompressedFile("puzzle.jpz") }),
    PDF("pdf", {
        it.asPdf(
            fontFamily = PdfFonts.getNotoFontFamily(),
            blackSquareLightnessAdjustment = Settings.getPdfInkSaverPercentage() / 100f
        )
    }, {
        it.asPdf(
            fontFamily = PdfFonts.getNotoFontFamily(),
            blackSquareLightnessAdjustment = Settings.getPdfInkSaverPercentage() / 100f
        )
    }),
}