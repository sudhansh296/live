package com.coderlobby.hivo.profile

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The arithmetic behind the profile-photo crop screen, kept free of Android types so it can be unit tested.
 *
 * Picture coordinates are bitmap pixels. Screen values are pixels. `offset` is where the picture's centre sits
 * relative to the crop window's centre. The crop window is a square (shown as a circle) in the middle of the view.
 */
object CropMath {
    const val MAX_ZOOM = 8f

    /** Screen pixels per bitmap pixel at which the picture exactly covers the crop window (zoom = 1). */
    fun coverScale(window: Float, bitmapWidth: Int, bitmapHeight: Int): Float =
        window / min(bitmapWidth, bitmapHeight)

    /** Stops the picture from being dragged so far that an empty gap shows inside the crop window. */
    fun clampOffset(offset: Float, pictureSize: Float, window: Float): Float {
        val limit = max(0f, (pictureSize - window) / 2f)
        return offset.coerceIn(-limit, limit)
    }

    /** The square, in bitmap pixels, that ends up inside the crop window. */
    data class Square(val x: Int, val y: Int, val size: Int)

    fun cropSquare(window: Float, scale: Float, offsetX: Float, offsetY: Float, bitmapWidth: Int, bitmapHeight: Int): Square {
        val size = (window / scale).roundToInt().coerceIn(1, min(bitmapWidth, bitmapHeight))
        val centreX = bitmapWidth / 2f - offsetX / scale
        val centreY = bitmapHeight / 2f - offsetY / scale
        val x = (centreX - size / 2f).roundToInt().coerceIn(0, bitmapWidth - size)
        val y = (centreY - size / 2f).roundToInt().coerceIn(0, bitmapHeight - size)
        return Square(x, y, size)
    }
}
