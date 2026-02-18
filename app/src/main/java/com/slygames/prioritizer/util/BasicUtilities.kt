package com.slygames.prioritizer.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream

fun generateFriendCode(): String {
    val alphabet = ('A'..'Z') + ('0'..'9')
    return List(3) { alphabet.random() }.joinToString("-") {
        List(2) { alphabet.random() }.joinToString("")
    } // e.g., "JX-4Z-K7"
}