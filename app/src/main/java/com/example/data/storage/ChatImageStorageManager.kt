package com.example.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.firebase.Firebase
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object ChatImageStorageManager {

    fun createCameraPhotoUri(context: Context): Uri {
        val photoDir = File(context.cacheDir, "chat_photos").apply {
            if (!exists()) mkdirs()
        }
        val photoFile = File(photoDir, "mec_chat_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    suspend fun compressAndUploadChatImage(
        context: Context,
        channelId: String,
        userId: String,
        imageUri: Uri,
        onProgress: (Float) -> Unit
    ): String {
        val jpegBytes = withContext(Dispatchers.IO) {
            compressUriToJpeg(context, imageUri)
        }
        val safeChannel = channelId.trim().lowercase().ifBlank { "general" }
        val fileName = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        val storageRef = Firebase.storage.reference
            .child("chat_images")
            .child(safeChannel)
            .child(userId)
            .child(fileName)

        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()

        val uploadTask = storageRef.putBytes(jpegBytes, metadata)
        uploadTask.addOnProgressListener { snapshot ->
            val total = snapshot.totalByteCount
            if (total > 0L) {
                onProgress((snapshot.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 1f))
            }
        }
        uploadTask.await()
        onProgress(1f)
        return storageRef.downloadUrl.await().toString()
    }

    private fun compressUriToJpeg(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1600,
        quality: Int = 85
    ): ByteArray {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: throw IllegalStateException("Unable to read captured image")

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
        }
        val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("Unable to decode captured image")

        val rotationDegrees = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val orientedBitmap = if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true).also {
                if (it !== rawBitmap) rawBitmap.recycle()
            }
        } else {
            rawBitmap
        }

        val scaledBitmap = scaleDownIfNeeded(orientedBitmap, maxDimension)
        val output = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        scaledBitmap.recycle()
        return output.toByteArray()
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / maxOf(width, height).toFloat()
        val targetW = (width * ratio).toInt().coerceAtLeast(1)
        val targetH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }
}
