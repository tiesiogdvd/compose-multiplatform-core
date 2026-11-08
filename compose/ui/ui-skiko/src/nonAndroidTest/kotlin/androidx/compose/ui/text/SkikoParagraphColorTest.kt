/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.text

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A paint in another colour draws the glyphs in it, without changing the layout. */
class SkikoParagraphColorTest : SkikoComposeTestBase() {
    private val fontFamilyResolver by lazy { createFontFamilyResolver() }

    private fun paragraph(text: String, maxWidth: Int, maxLines: Int = Int.MAX_VALUE) = Paragraph(
        text = text,
        style = TextStyle(fontSize = 20.sp, color = Color.Red),
        constraints = Constraints(maxWidth = maxWidth),
        density = Density(1f),
        fontFamilyResolver = fontFamilyResolver,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )

    /** How many pixels are mostly [channel] (0 red, 1 green, 2 blue). */
    private fun Paragraph.paintedPixels(color: Color, channel: Int): Int {
        val image = ImageBitmap(ceil(minOf(width, 1000f)).toInt().coerceAtLeast(1), ceil(height).toInt().coerceAtLeast(1))
        paint(Canvas(image), color = color)
        val pixels = image.toPixelMap()
        var count = 0
        for (x in 0 until image.width) for (y in 0 until image.height) {
            val p = pixels[x, y]
            val c = floatArrayOf(p.red, p.green, p.blue)
            if (p.alpha > 0.5f && c[channel] > 0.5f && c.indices.all { it == channel || c[it] < 0.2f }) count++
        }
        return count
    }

    private fun checkRecolours(paragraph: Paragraph) {
        val lines = paragraph.lineCount
        val height = paragraph.height
        assertTrue(paragraph.paintedPixels(Color.Red, 0) > 0, "red glyphs")
        repeat(3) {
            val blue = paragraph.paintedPixels(Color.Blue, 2)
            assertTrue(blue > 0, "blue glyphs")
            assertEquals(0, paragraph.paintedPixels(Color.Blue, 0), "no red left after the change to blue")
            assertTrue(paragraph.paintedPixels(Color.Green, 1) > 0, "green glyphs")
            assertEquals(0, paragraph.paintedPixels(Color.Green, 2), "no blue left after the change to green")
        }
        assertEquals(lines, paragraph.lineCount)
        assertEquals(height, paragraph.height)
    }

    @Test
    fun singleLine() = checkRecolours(paragraph("WWW", maxWidth = 1000).also { assertEquals(1, it.lineCount) })

    @Test
    fun severalLines() =
        checkRecolours(paragraph("WWW WWW WWW WWW WWW", maxWidth = 80).also { assertTrue(it.lineCount > 1) })

    // Constraints.Infinity's width (Int.MAX_VALUE) is finite and too large for a pixel more to change it.
    @Test
    fun hardLineBreaksAtUnboundedWidth() =
        checkRecolours(paragraph("WWW\nWWW\nWWW", maxWidth = Constraints.Infinity).also { assertEquals(3, it.lineCount) })

    @Test
    fun ellipsized() = checkRecolours(paragraph("WWW WWW WWW WWW WWW", maxWidth = 80, maxLines = 1))
}
