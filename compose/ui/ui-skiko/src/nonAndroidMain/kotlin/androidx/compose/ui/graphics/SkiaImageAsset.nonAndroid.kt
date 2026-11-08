/*
 * Copyright 2020 The Android Open Source Project
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

@file:JvmName("SkiaImageAsset_skikoKt")

package androidx.compose.ui.graphics

import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.jvm.JvmName
import kotlin.math.abs
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace as SkColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * Create an [ImageBitmap] from the given [Bitmap]. Note this does
 * not create a copy of the original [Bitmap] and changes to it
 * will modify the returned [ImageBitmap]
 */
fun Bitmap.asComposeImageBitmap(): ImageBitmap = SkiaBackedImageBitmap(this, pixelsOwned = false)

/**
 * Create an [ImageBitmap] from the given [Image].
 */
fun Image.toComposeImageBitmap(): ImageBitmap = SkiaBackedImageBitmap(toBitmap(), pixelsOwned = true)

// Split into expect/actual to use a faster implementation for web
// See web implementation for details and the reason.
internal expect fun Image.toBitmap(): Bitmap

/**
 * Obtain a reference to the [org.jetbrains.skia.Bitmap]
 *
 * @Throws UnsupportedOperationException if this [ImageBitmap] is not backed by an
 * org.jetbrains.skia.Image
 */
fun ImageBitmap.asSkiaBitmap(): Bitmap {
    val image = requireSkiaBacked()
    // The caller may change the pixels behind Compose's back from now on.
    image.disownPixels()
    return image.bitmap
}

/** The [SkiaBackedImageBitmap] behind this [ImageBitmap], for Compose's own use (it does not disown the pixels). */
internal fun ImageBitmap.requireSkiaBacked(): SkiaBackedImageBitmap =
    this as? SkiaBackedImageBitmap
        ?: throw UnsupportedOperationException("Unable to obtain org.jetbrains.skia.Image")

/**
 * @param pixelsOwned whether the pixels change only through Compose: drawing through a Canvas(ImageBitmap) of it
 * (SkikoGraphics.createCanvas), which [invalidateImage]s it. A Bitmap the caller wraps ([asComposeImageBitmap]) or
 * takes out ([asSkiaBitmap]) can change without Compose knowing, so its draws snapshot it every time.
 */
internal class SkiaBackedImageBitmap(val bitmap: Bitmap, private var pixelsOwned: Boolean) : ImageBitmap {
    override val colorSpace = bitmap.colorSpace.toComposeColorSpace()
    override val config = bitmap.colorType.toComposeConfig()
    override val hasAlpha = !bitmap.isOpaque
    override val height get() = bitmap.height
    override val width get() = bitmap.width

    /**
     * The Image draws use while the pixels are unchanged. Image.makeFromBitmap copies a mutable bitmap and gives the copy
     * a new id, so a snapshot per draw is a new texture per draw on the GPU (a copy and an upload per icon per frame:
     * VectorPainter draws through a DrawCache bitmap); one Image per content keeps the GPU's cached texture.
     */
    private var cachedImage: Image? = null

    // DrawCache calls this after drawing new content into the bitmap.
    override fun prepareToDraw() = invalidateImage()

    /** The pixels may have changed: the next draw takes a new snapshot. */
    fun invalidateImage() {
        val image = cachedImage ?: return
        cachedImage = null
        image.close()
    }

    fun disownPixels() {
        pixelsOwned = false
        invalidateImage()
    }

    /** The Image to draw, kept until the pixels change; null when they are not [pixelsOwned] (snapshot every draw). */
    fun cachedImageOrNull(): Image? {
        if (!pixelsOwned) return null
        return cachedImage ?: Image.makeFromBitmap(bitmap).also { cachedImage = it }
    }

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int
    ) {
        // similar to https://cs.android.com/android/platform/superproject/+/42c50042d1f05d92ecc57baebe3326a57aeecf77:frameworks/base/graphics/java/android/graphics/Bitmap.java;l=2007
        val lastScanline: Int = bufferOffset + (height - 1) * stride
        require(startX >= 0 && startY >= 0)
        require(width > 0 && startX + width <= this.width)
        require(height > 0 && startY + height <= this.height)
        require(abs(stride) >= width)
        require(bufferOffset >= 0 && bufferOffset + width <= buffer.size)
        require(lastScanline >= 0 && lastScanline + width <= buffer.size)

        // similar to https://cs.android.com/android/platform/superproject/+/9054ca2b342b2ea902839f629e820546d8a2458b:frameworks/base/libs/hwui/jni/Bitmap.cpp;l=898;bpv=1
        val colorInfo = ColorInfo(
            ColorType.BGRA_8888,
            ColorAlphaType.UNPREMUL,
            SkColorSpace.sRGB
        )
        val imageInfo = ImageInfo(colorInfo, width, height)
        val bytesPerPixel = 4
        val bytes = bitmap.readPixels(imageInfo, stride * bytesPerPixel, startX, startY)!!
        bytes.putBytesInto(buffer, bufferOffset, bytes.size / bytesPerPixel)
    }
}

internal expect fun ByteArray.putBytesInto(array: IntArray, offset: Int, length: Int)

// TODO(demin): [API] maybe we should use:
//  `else -> throw UnsupportedOperationException()`
//  in toSkiaColorType/toComposeConfig/toComposeColorSpace/toSkiaColorSpace
//  see [https://android-review.googlesource.com/c/platform/frameworks/support/+/1429835/comment/c219501b_63c3d1fe/]

private fun ImageBitmapConfig.toSkiaColorType() = when (this) {
    ImageBitmapConfig.Argb8888 -> ColorType.N32
    ImageBitmapConfig.Alpha8 -> ColorType.ALPHA_8
    ImageBitmapConfig.Rgb565 -> ColorType.RGB_565
    ImageBitmapConfig.F16 -> ColorType.RGBA_F16
    else -> ColorType.N32
}

private fun ColorType.toComposeConfig() = when (this) {
    ColorType.N32 -> ImageBitmapConfig.Argb8888
    ColorType.ALPHA_8 -> ImageBitmapConfig.Alpha8
    ColorType.RGB_565 -> ImageBitmapConfig.Rgb565
    ColorType.RGBA_F16 -> ImageBitmapConfig.F16
    else -> ImageBitmapConfig.Argb8888
}

private fun SkColorSpace?.toComposeColorSpace(): ColorSpace {
    return when (this) {
        SkColorSpace.sRGB -> ColorSpaces.Srgb
        SkColorSpace.sRGBLinear -> ColorSpaces.LinearSrgb
        SkColorSpace.displayP3 -> ColorSpaces.DisplayP3
        else -> ColorSpaces.Srgb
    }
}

// TODO(demin): support all color spaces.
//  to do this we need to implement SkColorSpace::MakeRGB in skia
private fun ColorSpace.toSkiaColorSpace(): SkColorSpace {
    return when (this) {
        ColorSpaces.Srgb -> SkColorSpace.sRGB
        ColorSpaces.LinearSrgb -> SkColorSpace.sRGBLinear
        ColorSpaces.DisplayP3 -> SkColorSpace.displayP3
        else -> SkColorSpace.sRGB
    }
}
