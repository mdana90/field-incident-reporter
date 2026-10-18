package com.company.fieldincidentreporter

import android.net.Uri
import com.company.fieldincidentreporter.data.CameraPhoto
import com.company.fieldincidentreporter.data.DraftPhotoStore
import com.company.fieldincidentreporter.data.resultOf
import com.company.fieldincidentreporter.data.validateJpegFile
import com.company.fieldincidentreporter.model.Result
import java.io.File

internal class FakeDraftPhotoStore : DraftPhotoStore {
    val deletedDrafts = mutableListOf<String>()
    var onDeleteDraft: ((String) -> Unit)? = null

    override fun newCameraPhoto(requestId: String): CameraPhoto = error("Unused by these tests")
    override suspend fun copyPickedPhoto(requestId: String, uri: Uri): Result<File> = error("Unused by these tests")
    override suspend fun acceptCameraPhoto(requestId: String, file: File): Result<File> = Result.Success(file)
    override suspend fun validatePhotos(files: List<File>): Result<Unit> = resultOf { files.forEach(::validateJpegFile) }
    override fun deletePhoto(file: File) { file.delete() }
    override fun deleteDraft(requestId: String) {
        deletedDrafts += requestId
        onDeleteDraft?.invoke(requestId)
    }
}
