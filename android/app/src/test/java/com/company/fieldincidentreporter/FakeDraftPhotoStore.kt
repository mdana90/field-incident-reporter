package com.company.fieldincidentreporter

import android.net.Uri
import com.company.fieldincidentreporter.data.CameraPhoto
import com.company.fieldincidentreporter.data.DraftPhotoStore
import java.io.File

internal class FakeDraftPhotoStore : DraftPhotoStore {
    val deletedDrafts = mutableListOf<String>()
    var onDeleteDraft: ((String) -> Unit)? = null

    override fun newCameraPhoto(requestId: String): CameraPhoto = error("Unused by these tests")
    override suspend fun copyPickedPhoto(requestId: String, uri: Uri): File = error("Unused by these tests")
    override suspend fun acceptCameraPhoto(requestId: String, file: File): File = file
    override fun deletePhoto(file: File) { file.delete() }
    override fun deleteDraft(requestId: String) {
        deletedDrafts += requestId
        onDeleteDraft?.invoke(requestId)
    }
}
