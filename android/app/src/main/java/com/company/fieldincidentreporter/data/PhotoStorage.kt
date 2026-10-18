package com.company.fieldincidentreporter.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoStorage @Inject constructor(@ApplicationContext private val context: Context) {
    fun newCameraUri(): Uri {
        val directory = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    suspend fun copyToTemporaryFile(uri: Uri): File = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri)
        val suffix = when (mime) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/heic" -> ".heic"
            "image/heif" -> ".heif"
            else -> ".jpg"
        }
        val directory = File(context.cacheDir, "selected").apply { mkdirs() }
        val target = File(directory, "${UUID.randomUUID()}$suffix")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val copied = input.copyTo(output)
                    if (copied > 10L * 1024 * 1024) throw IOException("Photo must be under 10 MiB")
                }
            } ?: throw IOException("Could not read this photo")
            target
        } catch (failure: Exception) {
            target.delete()
            throw failure
        }
    }
}
