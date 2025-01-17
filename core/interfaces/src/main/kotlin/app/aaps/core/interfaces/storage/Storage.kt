package app.aaps.core.interfaces.storage

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import java.io.File

// This may seems unnecessary abstraction - but it will simplify testing
interface Storage {

    fun putFileContents(file: File, contents: String)
    @Throws(SecurityException::class)
    fun putFileContents(contentResolver: ContentResolver, file: DocumentFile, contents: String)
    fun getFileContents(file: File): String
    @Throws(SecurityException::class)
    fun getFileContents(contentResolver: ContentResolver, file: DocumentFile): String
    @Throws(SecurityException::class)
    fun getBinaryFileContents(contentResolver: ContentResolver, file: DocumentFile): ByteArray?
}
