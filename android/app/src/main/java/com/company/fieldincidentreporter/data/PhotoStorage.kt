package com.company.fieldincidentreporter.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.company.fieldincidentreporter.model.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CameraPhoto(val uri: Uri, val file: File)

interface DraftPhotoStore {
    fun newCameraPhoto(requestId: String): CameraPhoto
    suspend fun copyPickedPhoto(requestId: String, uri: Uri): Result<File>
    suspend fun acceptCameraPhoto(requestId: String, file: File): Result<File>
    suspend fun validatePhotos(files: List<File>): Result<Unit>
    fun deletePhoto(file: File)
    fun deleteDraft(requestId: String)
}

class PhotoStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DraftPhotoStore {
    override fun newCameraPhoto(requestId: String): CameraPhoto {
        val directory = draftDirectory(requestId).apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return CameraPhoto(uri, file)
    }

    override suspend fun copyPickedPhoto(requestId: String, uri: Uri): Result<File> = resultOf {
        withContext(Dispatchers.IO) {
            val mime = context.contentResolver.getType(uri)
            if (mime != null && mime != "image/jpeg") throw IOException("Photo must be a JPEG")
            val target = File(draftDirectory(requestId).apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IOException("Could not read this photo")
                validateJpegFile(target)
                target
            } catch (failure: Exception) {
                target.delete()
                throw failure
            }
        }
    }

    override suspend fun acceptCameraPhoto(requestId: String, file: File): Result<File> = resultOf {
        withContext(Dispatchers.IO) {
            if (file.parentFile?.canonicalFile != draftDirectory(requestId).canonicalFile) {
                throw IOException("Could not read this photo")
            }
            validateJpegFile(file)
            file
        }
    }

    override suspend fun validatePhotos(files: List<File>): Result<Unit> = resultOf {
        withContext(Dispatchers.IO) { files.forEach(::validateJpegFile) }
    }

    override fun deletePhoto(file: File) { file.delete() }

    override fun deleteDraft(requestId: String) { draftDirectory(requestId).deleteRecursively() }

    private fun draftDirectory(requestId: String) = File(context.filesDir, "drafts/$requestId")

}

internal fun validateJpegFile(file: File) {
    if (!file.isFile || file.length() == 0L) throw IOException("Could not read this photo")
    if (file.length() > 10L * 1024 * 1024) throw IOException("Photo must be under 10 MiB")
    file.inputStream().use { input ->
        if (input.read() != 0xff || input.read() != 0xd8) throw IOException("Photo must be a JPEG")
    }
}
