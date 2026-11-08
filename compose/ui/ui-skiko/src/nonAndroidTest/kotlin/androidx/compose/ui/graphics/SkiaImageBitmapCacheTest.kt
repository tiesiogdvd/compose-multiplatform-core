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

package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.SkikoComposeTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.jetbrains.skia.Canvas as SkCanvas

/**
 * An ImageBitmap's draws reuse one Image while its pixels are unchanged (one GPU texture), and show every change made
 * through Compose (Canvas(ImageBitmap), prepareToDraw) or through the Bitmap taken out with asSkiaBitmap().
 */
class SkiaImageBitmapCacheTest : SkikoComposeTestBase() {

    private fun ImageBitmap.fill(canvas: Canvas, color: Color) =
        canvas.drawRect(Rect(Offset.Zero, androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat())), Paint().apply { this.color = color })

    private fun drawnColor(source: ImageBitmap): Color {
        val dst = ImageBitmap(4, 4)
        Canvas(dst).drawImage(source, Offset.Zero, Paint())
        return dst.toPixelMap()[1, 1]
    }

    @Test
    fun drawsReuseOneImageWhileUnchanged() {
        val source = ImageBitmap(4, 4)
        source.fill(Canvas(source), Color.Red)
        val skiaBacked = source.requireSkiaBacked()
        assertEquals(Color.Red, drawnColor(source))
        val first = assertNotNull(skiaBacked.cachedImageOrNull(), "ImageBitmap(w, h) is cached")
        assertEquals(Color.Red, drawnColor(source))
        assertSame(first, skiaBacked.cachedImageOrNull())
    }

    @Test
    fun drawingThroughItsCanvasShowsOnTheNextDraw() {
        val source = ImageBitmap(4, 4)
        val canvas = Canvas(source)
        source.fill(canvas, Color.Red)
        assertEquals(Color.Red, drawnColor(source))
        // No prepareToDraw: the canvas's draw alone makes the cached Image stale.
        source.fill(canvas, Color.Blue)
        assertEquals(Color.Blue, drawnColor(source))
    }

    @Test
    fun prepareToDrawShowsTheNewContent() {
        val source = ImageBitmap(4, 4)
        val canvas = Canvas(source)
        source.fill(canvas, Color.Red)
        assertEquals(Color.Red, drawnColor(source))
        source.fill(canvas, Color.Green)
        source.prepareToDraw()
        assertEquals(Color.Green, drawnColor(source))
    }

    @Test
    fun aBitmapTakenOutIsSnapshotOnEveryDraw() {
        val source = ImageBitmap(4, 4)
        source.fill(Canvas(source), Color.Red)
        assertEquals(Color.Red, drawnColor(source))
        val bitmap = source.asSkiaBitmap()
        assertNull(source.requireSkiaBacked().cachedImageOrNull())
        SkCanvas(bitmap).clear(Color.Blue.toArgb())
        assertEquals(Color.Blue, drawnColor(source))
        SkCanvas(bitmap).clear(Color.Green.toArgb())
        assertEquals(Color.Green, drawnColor(source))
    }

    @Test
    fun aDecodedImageIsCached() {
        val image = ImageBitmap(4, 4).also { it.fill(Canvas(it), Color.Red) }
        val decoded = org.jetbrains.skia.Image.makeFromBitmap(image.requireSkiaBacked().bitmap).toComposeImageBitmap()
        assertNotNull(decoded.requireSkiaBacked().cachedImageOrNull())
        assertEquals(Color.Red, drawnColor(decoded))
    }

    @Test
    fun aWrappedBitmapIsSnapshotOnEveryDraw() {
        val bitmap = org.jetbrains.skia.Bitmap().apply { allocN32Pixels(4, 4) }
        val source = bitmap.asComposeImageBitmap()
        assertNull(source.requireSkiaBacked().cachedImageOrNull())
        SkCanvas(bitmap).clear(Color.Red.toArgb())
        assertEquals(Color.Red, drawnColor(source))
        SkCanvas(bitmap).clear(Color.Blue.toArgb())
        assertEquals(Color.Blue, drawnColor(source))
    }
}
