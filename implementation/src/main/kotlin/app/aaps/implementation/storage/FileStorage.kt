package app.aaps.implementation.storage

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.storage.Storage
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorage @Inject constructor() : Storage {

    override fun getFileContents(file: File): String {
        return file.readText()
    }

    @Throws(SecurityException::class)
    override fun getFileContents(contentResolver: ContentResolver, file: DocumentFile): String {
        val inputStream = contentResolver.openInputStream(file.uri)
        val reader = BufferedReader(InputStreamReader(inputStream))
        return reader.readText()
    }

    override fun getBinaryFileContents(contentResolver: ContentResolver, file: DocumentFile): ByteArray? {
        val inputStream = contentResolver.openInputStream(file.uri)
        val byteArray = inputStream?.readBytes()
        inputStream?.close()
        return byteArray
    }

    override fun putFileContents(file: File, contents: String) {
        file.writeText(contents)
    }

    override fun putFileContents(contentResolver: ContentResolver, file: DocumentFile, contents: String) {
        val output = FileOutputStream(contentResolver.openFileDescriptor(file.uri, "w")?.fileDescriptor)
        output.write(contents.toByteArray())
        output.flush()
        output.close()
    }

}