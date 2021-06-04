package com.jeffpdavidson.crosswordscraper

import com.jeffpdavidson.kotwords.formats.AcrossLite.Companion.toAcrossLiteBinary
import com.jeffpdavidson.kotwords.formats.Jpz.Companion.toJpz
import com.jeffpdavidson.kotwords.model.Crossword

/** Supported output file formats. */
enum class FileFormat(val extension: String, val toBinary: suspend (Crossword) -> ByteArray) {
    ACROSS_LITE("puz", { it.toAcrossLiteBinary() }),
    JPZ("jpz", { it.toJpz().toCompressedFile("puzzle.jpz") })
}