package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.data.validateJpegFile
import java.io.IOException
import java.io.RandomAccessFile
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
        assertThrows(IOException::class.java) { validateJpegFile(wrongType) }
        assertThrows(IOException::class.java) { validateJpegFile(files.newFile("empty.jpg")) }
    }

    @Test fun rejectsOversizedCameraOutput() {
        val photo = files.newFile("large.jpg")
        RandomAccessFile(photo, "rw").use { it.setLength(10L * 1024 * 1024 + 1) }
        assertThrows(IOException::class.java) { validateJpegFile(photo) }
    }
}
