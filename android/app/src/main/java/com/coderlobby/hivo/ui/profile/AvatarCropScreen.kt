package com.coderlobby.hivo.ui.profile

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coderlobby.hivo.R
import com.coderlobby.hivo.profile.CropMath
import com.coderlobby.hivo.ui.theme.HivoRed
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lets the user choose which part of a picture becomes the profile photo: drag to move, pinch to zoom.
 * The circle is what other people will see; the app uploads the square around it.
 */
@Composable
fun AvatarCropScreen(source: Bitmap, onCancel: () -> Unit, onDone: (CropMath.Square) -> Unit) {
    BackHandler(onBack = onCancel)

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    // Zoom is relative: 1 = the picture just covers the circle. Offset = picture centre minus circle centre, in pixels.
    var zoom by remember(source) { mutableFloatStateOf(1f) }
    var offset by remember(source) { mutableStateOf(Offset.Zero) }

    val image = remember(source) { source.asImageBitmap() }
    val window = min(viewSize.width, viewSize.height) * WINDOW_SHARE
    val ready = window > 0f
    val baseScale = if (ready) CropMath.coverScale(window, source.width, source.height) else 1f

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        Text(
            stringResource(R.string.crop_hint),
            color = Color(0xFFBBBBBB),
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        // Clipped, because a zoomed picture is far bigger than this area and must not paint over the buttons.
        Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { viewSize = it }) {
            Canvas(
                modifier = Modifier.fillMaxSize().pointerInput(source, viewSize) {
                    if (!ready) return@pointerInput
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, CropMath.MAX_ZOOM)
                        val ratio = newZoom / zoom
                        // Zoom around the fingers, not the middle: keep the point under them where it is.
                        val around = Offset(centroid.x - viewSize.width / 2f, centroid.y - viewSize.height / 2f)
                        val moved = (offset - around) * ratio + around + pan
                        val scale = baseScale * newZoom
                        offset = Offset(
                            CropMath.clampOffset(moved.x, source.width * scale, window),
                            CropMath.clampOffset(moved.y, source.height * scale, window),
                        )
                        zoom = newZoom
                    }
                },
            ) {
                val scale = baseScale * zoom
                val width = source.width * scale
                val height = source.height * scale
                val centre = Offset(size.width / 2f, size.height / 2f)
                drawImage(
                    image = image,
                    dstOffset = IntOffset((centre.x + offset.x - width / 2f).roundToInt(), (centre.y + offset.y - height / 2f).roundToInt()),
                    dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
                if (ready) {
                    // Darken everything outside the circle, then outline it.
                    val outside = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        addOval(Rect(centre, window / 2f))
                    }
                    drawPath(outside, Color(0xB3000000))
                    drawCircle(Color.White, radius = window / 2f, center = centre, style = Stroke(width = 1.5.dp.toPx()))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel), color = Color.White, fontSize = 15.sp)
            }
            Button(
                onClick = {
                    onDone(CropMath.cropSquare(window, baseScale * zoom, offset.x, offset.y, source.width, source.height))
                },
                enabled = ready,
                colors = ButtonDefaults.buttonColors(containerColor = HivoRed, contentColor = Color.White),
            ) {
                Text(stringResource(R.string.crop_done), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// The circle takes this share of the shorter side of the view, leaving a margin so its edge stays visible.
private const val WINDOW_SHARE = 0.86f
