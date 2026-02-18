package com.slygames.prioritizer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayInputStream
import java.io.File

fun saveProfileImageToInternalStorage(context: Context, imageBytes: ByteArray): String {
    val file = File(context.filesDir, "profile_image.jpg")
    file.writeBytes(imageBytes)
    return file.absolutePath
}

fun deleteOldProfileImage(path: String?) {
    path?.let {
        val file = File(it)
        if (file.exists()) file.delete()
    }
}

fun loadProfileImage(path: String?): ImageBitmap? {
    if (path == null) return null
    val file = File(path)
    if (!file.exists()) return null

    return try {
        val exif = ExifInterface(file)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val rotated = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            else -> bitmap
        }

        rotated.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

fun decodeImageWithCorrectOrientation(imageBytes: ByteArray): Bitmap {
    val stream = ByteArrayInputStream(imageBytes)
    val exif = ExifInterface(stream)

    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )

    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
        else -> bitmap
    }
}

fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}
