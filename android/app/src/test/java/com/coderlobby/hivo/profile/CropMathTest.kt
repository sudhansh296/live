package com.coderlobby.hivo.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class CropMathTest {
    @Test
    fun coverScaleFitsTheShorterSideToTheWindow() {
        assertEquals(0.5f, CropMath.coverScale(500f, 1000, 2000), 0.0001f)
        assertEquals(0.25f, CropMath.coverScale(500f, 4000, 2000), 0.0001f)
    }

    @Test
    fun untouchedTallPictureCropsTheMiddle() {
        // 1000x2000 picture, window 500 px, zoom 1 -> scale 0.5, whole width, middle band of the height.
        val square = CropMath.cropSquare(500f, 0.5f, 0f, 0f, 1000, 2000)
        assertEquals(CropMath.Square(0, 500, 1000), square)
    }

    @Test
    fun zoomingInCropsASmallerCentredSquare() {
        val square = CropMath.cropSquare(500f, 1.0f, 0f, 0f, 1000, 2000)
        assertEquals(CropMath.Square(250, 750, 500), square)
    }

    @Test
    fun draggingThePictureLeftMovesTheCropRight() {
        val square = CropMath.cropSquare(500f, 1.0f, -100f, 0f, 1000, 2000)
        assertEquals(CropMath.Square(350, 750, 500), square)
    }

    @Test
    fun theCropNeverLeavesThePicture() {
        val far = CropMath.cropSquare(500f, 1.0f, -9999f, 9999f, 1000, 2000)
        assertEquals(CropMath.Square(500, 0, 500), far)
        val other = CropMath.cropSquare(500f, 1.0f, 9999f, -9999f, 1000, 2000)
        assertEquals(CropMath.Square(0, 1500, 500), other)
    }

    @Test
    fun offsetIsClampedSoNoEmptyGapShowsInsideTheWindow() {
        // Picture 1000 px wide on screen, window 500 -> the centre may move at most 250 px either way.
        assertEquals(250f, CropMath.clampOffset(400f, 1000f, 500f), 0.0001f)
        assertEquals(-250f, CropMath.clampOffset(-400f, 1000f, 500f), 0.0001f)
        assertEquals(120f, CropMath.clampOffset(120f, 1000f, 500f), 0.0001f)
        // A picture exactly as big as the window cannot move at all.
        assertEquals(0f, CropMath.clampOffset(80f, 500f, 500f), 0.0001f)
    }
}
