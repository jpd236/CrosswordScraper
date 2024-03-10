package com.jeffpdavidson.crosswordscraper

import com.jeffpdavidson.kotwords.formats.AcrossLite.Companion.supportsAcrossLite
import com.jeffpdavidson.kotwords.formats.Puzzleable
import com.jeffpdavidson.kotwords.model.Puzzle
import isFirefoxForAndroid

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
    IPUZ(
        "ipuz",
        { it.puzzleType in listOf(Puzzle.PuzzleType.CROSSWORD, Puzzle.PuzzleType.CODED) },
        { it.asIpuzFile() }),
    PDF("pdf",
        supportsPuzzle = {
            // Disable PDF on Firefox for Android for now: https://bugzilla.mozilla.org/show_bug.cgi?id=1877898
            !isFirefoxForAndroid()
        }, {
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