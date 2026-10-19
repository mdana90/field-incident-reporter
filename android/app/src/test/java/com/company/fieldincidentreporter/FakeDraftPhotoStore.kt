package com.company.fieldincidentreporter

import android.net.Uri
import com.company.fieldincidentreporter.data.CameraPhoto
import com.company.fieldincidentreporter.data.DraftPhotoStore
import com.company.fieldincidentreporter.data.resultOf
import com.company.fieldincidentreporter.data.validateJpegFile
import com.company.fieldincidentreporter.model.Result
import java.io.File
import kotlin.coroutines.ContinuationInterceptor

internal class FakeDraftPhotoStore : DraftPhotoStore {
    val deletedDrafts = mutableListOf<String>()
    val pickedUris = mutableListOf<Uri>()
    val dispatchers = mutableSetOf<ContinuationInterceptor?>()
    var onDeleteDraft: ((String) -> Unit)? = null
    var cameraPhoto: () -> CameraPhoto = { error("No camera photo configured") }
    var pickedPhoto: (Uri) -> File = { error("No picked photo configured") }

    override fun newCameraPhoto(requestId: String): CameraPhoto = cameraPhoto()
    override suspend fun copyPickedPhoto(requestId: String, uri: Uri): Result<File> {
        dispatchers += currentDispatcher()
        pickedUris += uri
        return resultOf { pickedPhoto(uri) }
    }
    override suspend fun acceptCameraPhoto(requestId: String, file: File): Result<File> {
        dispatchers += currentDispatcher()
        return Result.Success(file)
    }
    override suspend fun validatePhotos(files: List<File>): Result<Unit> {
        dispatchers += currentDispatcher()
        return resultOf { files.forEach(::validateJpegFile) }
    }
    override fun deletePhoto(file: File) { file.delete() }
    override fun deleteDraft(requestId: String) {
        deletedDrafts += requestId
        onDeleteDraft?.invoke(requestId)
    }
}
