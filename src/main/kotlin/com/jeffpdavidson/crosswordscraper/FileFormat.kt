package com.jeffpdavidson.crosswordscraper

import com.jeffpdavidson.kotwords.formats.AcrossLite.Companion.supportsAcrossLite
import com.jeffpdavidson.kotwords.formats.Puzzleable
import com.jeffpdavidson.kotwords.model.Puzzle

/** Supported output file formats. */
enum class FileFormat(
    val extension: String,
    val supportsPuzzle: (Puzzle) -> Boolean,
    val puzzleableToBinary: (suspend (Puzzleable) -> ByteArray),
) {
    PUZ(
        "puz",
        { it.supportsAcrossLite() },
        { it.asAcrossLiteBinary(writeUtf8 = Settings.isPuzUnicodeSupportEnabled()) }),
    JPZ("jpz", { true }, { it.asJpzFile() }),
    IPUZ("ipuz", { it.puzzleType == Puzzle.PuzzleType.CROSSWORD }, { it.asIpuzFile() }),
    PDF("pdf", { true }, {
        val fontFamily = if (Settings.getPdfFont() == "NotoSans") {
            PdfFonts.getNotoSansFontFamily()
        } else {
            PdfFonts.getNotoSerifFontFamily()
        }
        it.asPdf(
            fontFamily = fontFamily,
            blackSquareLightnessAdjustment = Settings.getPdfInkSaverPercentage() / 100f
        )
    }),
}