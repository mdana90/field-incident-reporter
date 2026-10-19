package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.data.PhotoException
import com.company.fieldincidentreporter.data.photoIo
import com.company.fieldincidentreporter.data.validateJpegFile
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JpegPhotoValidationTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun acceptsJpegAndRejectsOtherContents() {
        val jpeg = files.newFile("photo.jpg").apply { writeBytes(byteArrayOf(-1, -40, -1, -39)) }
        val wrongType = files.newFile("false.jpg").apply { writeBytes(byteArrayOf(-119, 80, 78, 71)) }
        validateJpegFile(jpeg)
        assertEquals("Photo must be a JPEG", assertThrows(PhotoException::class.java) { validateJpegFile(wrongType) }.message)
        assertEquals("Could not read this photo", assertThrows(PhotoException::class.java) { validateJpegFile(files.newFile("empty.jpg")) }.message)
    }

    @Test fun rejectsOversizedCameraOutput() {
        val photo = files.newFile("large.jpg")
        RandomAccessFile(photo, "rw").use { it.setLength(10L * 1024 * 1024 + 1) }
        assertEquals("Photo must be under 10 MiB", assertThrows(PhotoException::class.java) { validateJpegFile(photo) }.message)
    }

    @Test fun otherIoFailureWhileHandlingPhotoBecomesUnreadablePhoto() {
        val cause = FileNotFoundException("No content provider: content://gallery/1")
        val failure = assertThrows(PhotoException::class.java) { photoIo { throw cause } }
        assertEquals("Could not read this photo", failure.message)
        assertSame(cause, failure.cause)
    }

    @Test fun photoFailureKeepsItsOwnMessage() {
        val original = PhotoException("Photo must be a JPEG")
        assertSame(original, assertThrows(PhotoException::class.java) { photoIo { throw original } })
    }
}
