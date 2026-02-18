package com.slygames.prioritizer.ui.dialogs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.slygames.prioritizer.data.user.UserProfile
import com.slygames.prioritizer.util.loadProfileImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditProfileDialog(
        initialProfile: UserProfile?,
        onDismiss: () -> Unit,
        onSave: (String, ByteArray?) -> Unit
) {
    var nickname by remember { mutableStateOf(initialProfile?.nickname.orEmpty()) }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var removedImage by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // When user picks an image we open the cropper (with EXIF rotation applied)
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val scope = rememberCoroutineScope() // Ensure scope is available
    val pickImageLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()

                        // Temp file to read EXIF
                        val temp = File(context.cacheDir, "temp_profile_image.jpg")
                        FileOutputStream(temp).use { it.write(bytes) }

                        val rotated =
                                try {
                                    val exif = ExifInterface(temp.absolutePath)
                                    val orientation =
                                            exif.getAttributeInt(
                                                    ExifInterface.TAG_ORIENTATION,
                                                    ExifInterface.ORIENTATION_NORMAL
                                            )
                                    val original =
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    when (orientation) {
                                        ExifInterface.ORIENTATION_ROTATE_90 ->
                                                rotateBitmap(original, 90f)
                                        ExifInterface.ORIENTATION_ROTATE_180 ->
                                                rotateBitmap(original, 180f)
                                        ExifInterface.ORIENTATION_ROTATE_270 ->
                                                rotateBitmap(original, 270f)
                                        else -> original
                                    }
                                } catch (_: Exception) {
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                }

                        // Update UI state on Main thread
                        withContext(kotlinx.coroutines.Dispatchers.Main) { cropBitmap = rotated }
                    }
                }
            }

    // Current avatar preview
    val avatarBitmap =
            remember(imageBytes, initialProfile?.profileImagePath, removedImage) {
                when {
                    removedImage -> null
                    imageBytes != null && imageBytes!!.isNotEmpty() ->
                            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes!!.size)
                                    ?.asImageBitmap()
                    initialProfile?.profileImagePath != null ->
                            loadProfileImage(initialProfile.profileImagePath)
                    else -> null
                }
            }

    // Crop dialog (appears after selecting a new photo)
    cropBitmap?.let { toCrop ->
        CropProfileImageDialog(
                source = toCrop,
                onCancel = { cropBitmap = null },
                onCropped = { cropped ->
                    imageBytes = cropped.toJpegBytes(quality = 92)
                    removedImage = false
                    cropBitmap = null
                }
        )
    }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit Profile") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.BottomEnd) {
                        if (avatarBitmap != null) {
                            Image(
                                    bitmap = avatarBitmap,
                                    contentDescription = "Profile Image",
                                    modifier =
                                            Modifier.size(100.dp)
                                                    .clip(CircleShape)
                                                    .border(
                                                            2.dp,
                                                            MaterialTheme.colorScheme.primary,
                                                            CircleShape
                                                    )
                                                    .background(
                                                            MaterialTheme.colorScheme.surfaceVariant
                                                    )
                            )
                        } else {
                            Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Default Profile",
                                    modifier =
                                            Modifier.size(100.dp)
                                                    .clip(CircleShape)
                                                    .border(
                                                            2.dp,
                                                            MaterialTheme.colorScheme.primary,
                                                            CircleShape
                                                    )
                                                    .background(
                                                            MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                                modifier =
                                        Modifier.size(32.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surface)
                                                .border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.primary,
                                                        CircleShape
                                                )
                                                .clickable { pickImageLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Image",
                                    tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (avatarBitmap != null) {
                        TextButton(
                                onClick = {
                                    imageBytes = null
                                    removedImage = true
                                },
                                modifier = Modifier.padding(top = 6.dp)
                        ) { Text("Remove Image") }
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            label = { Text("Nickname") },
                            singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onSave(nickname.ifBlank { "NewUser" }, imageBytes) }) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Dialog to pinch-zoom & drag an image under a circular frame, then return a square crop matching
 * the visible circle.
 */
@Composable
private fun CropProfileImageDialog(
        source: Bitmap,
        onCancel: () -> Unit,
        onCropped: (Bitmap) -> Unit
) {
    val density = LocalContext.current.resources.displayMetrics.density

    // Viewport (square) and circle diameter — tune as desired
    val viewportSizeDp = 320.dp
    val circleDiameterDp = 260.dp

    val viewportPx = viewportSizeDp.value * density
    val circlePx = circleDiameterDp.value * density

    // Transform state
    var userScale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) } // px
    var offsetY by remember { mutableStateOf(0f) } // px

    // Base scale so the image initially covers the circle
    val minDim = min(source.width, source.height).toFloat()
    val baseScale = remember(source) { circlePx / minDim } // scale factor
    val totalScale = baseScale * userScale

    val pointerModifier =
            Modifier.pointerInput(source) {
                detectTransformGestures { _, pan, zoom, _ ->
                    userScale = (userScale * zoom).coerceIn(0.5f, 6f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }

    // Capture theme color outside Canvas (Canvas draw scope isn't @Composable)
    val dimColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)

    AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Adjust Profile Photo") },
            text = {
                Box(
                        modifier =
                                Modifier.size(viewportSizeDp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clip(MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                ) {
                    // Draw source at intrinsic size; only transform via graphicsLayer
                    Image(
                            bitmap = source.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.None, // don't auto-fit
                            alignment = Alignment.Center,
                            modifier =
                                    pointerModifier.wrapContentSize(
                                                    unbounded = true
                                            ) // use intrinsic size
                                            .graphicsLayer {
                                                // Transform in pixels
                                                translationX = offsetX
                                                translationY = offsetY
                                                scaleX = totalScale
                                                scaleY = totalScale
                                            }
                    )

                    // Dim the outside using an even-odd path (true transparent hole)
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val hole =
                                Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    // Outer full-rect
                                    addRect(
                                            androidx.compose.ui.geometry.Rect(
                                                    0f,
                                                    0f,
                                                    size.width,
                                                    size.height
                                            )
                                    )
                                    // Inner circle (hole)
                                    addOval(
                                            androidx.compose.ui.geometry.Rect(
                                                    left = (size.width - circlePx) / 2f,
                                                    top = (size.height - circlePx) / 2f,
                                                    right = (size.width + circlePx) / 2f,
                                                    bottom = (size.height + circlePx) / 2f
                                            )
                                    )
                                }
                        drawPath(hole, color = dimColor)
                    }

                    // Circular border on top for a crisp guide
                    Box(
                            modifier =
                                    Modifier.size(circleDiameterDp)
                                            .clip(CircleShape)
                                            .border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape
                                            )
                    )
                }
            },
            confirmButton = {
                TextButton(
                        onClick = {
                            val cropped =
                                    cropFromTransform(
                                            src = source,
                                            viewportPx = viewportPx,
                                            circlePx = circlePx,
                                            totalScale = totalScale,
                                            offsetX = offsetX,
                                            offsetY = offsetY
                                    )
                            onCropped(cropped)
                        }
                ) { Text("Use Photo") }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/**
 * Map on-screen transform (px) back to source bitmap coords and return a square crop that
 * corresponds to the visible circle. Output normalized to 512x512.
 */
private fun cropFromTransform(
        src: Bitmap,
        viewportPx: Float,
        circlePx: Float,
        totalScale: Float,
        offsetX: Float,
        offsetY: Float
): Bitmap {
    // Circle in viewport space
    val circleLeftV = (viewportPx - circlePx) / 2f
    val circleTopV = (viewportPx - circlePx) / 2f
    val circleRightV = circleLeftV + circlePx
    val circleBottomV = circleTopV + circlePx

    // Image drawn centered in viewport, then translated & scaled in px
    val viewportCenterX = viewportPx / 2f
    val viewportCenterY = viewportPx / 2f

    val imgW = src.width.toFloat()
    val imgH = src.height.toFloat()
    val drawW = imgW * totalScale
    val drawH = imgH * totalScale

    val imgLeftV = viewportCenterX - drawW / 2f + offsetX
    val imgTopV = viewportCenterY - drawH / 2f + offsetY

    // Intersection (viewport space)
    val interLeftV = maxOf(circleLeftV, imgLeftV)
    val interTopV = maxOf(circleTopV, imgTopV)
    val interRightV = minOf(circleRightV, imgLeftV + drawW)
    val interBottomV = minOf(circleBottomV, imgTopV + drawH)

    // If no overlap, fallback to center square
    if (interRightV <= interLeftV || interBottomV <= interTopV) {
        val side = min(src.width, src.height)
        val left = (src.width - side) / 2
        val top = (src.height - side) / 2
        val square = Bitmap.createBitmap(src, left, top, side, side)
        return Bitmap.createScaledBitmap(square, 512, 512, true)
    }

    // Map viewport -> source pixels
    fun v2sX(vx: Float) = (vx - imgLeftV) / totalScale
    fun v2sY(vy: Float) = (vy - imgTopV) / totalScale

    var leftSrc = v2sX(interLeftV)
    var topSrc = v2sY(interTopV)
    var rightSrc = v2sX(interRightV)
    var bottomSrc = v2sY(interBottomV)

    // Clamp to source bounds
    leftSrc = leftSrc.coerceIn(0f, imgW)
    topSrc = topSrc.coerceIn(0f, imgH)
    rightSrc = rightSrc.coerceIn(0f, imgW)
    bottomSrc = bottomSrc.coerceIn(0f, imgH)

    var cropW = max(1, (rightSrc - leftSrc).toInt())
    var cropH = max(1, (bottomSrc - topSrc).toInt())

    // Make square to match circle aperture
    val side = min(cropW, cropH)
    cropW = side
    cropH = side

    val safeLeft = leftSrc.toInt().coerceIn(0, (src.width - side).coerceAtLeast(0))
    val safeTop = topSrc.toInt().coerceIn(0, (src.height - side).coerceAtLeast(0))

    val square = Bitmap.createBitmap(src, safeLeft, safeTop, cropW, cropH)
    return Bitmap.createScaledBitmap(square, 512, 512, true)
}

/** Rotate a bitmap by the given degrees. */
private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
    if (degrees % 360f == 0f) return src
    val m = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

/** Convert Bitmap to JPEG bytes. */
private fun Bitmap.toJpegBytes(quality: Int = 92): ByteArray {
    val baos = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.JPEG, quality, baos)
    return baos.toByteArray()
}
