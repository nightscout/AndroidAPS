package app.aaps.core.interfaces.storage

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import java.io.File

// This may seems unnecessary abstraction - but it will simplify testing
interface Storage {

    fun putFileContents(file: File, contents: String)
    fun putFileContents(contentResolver: ContentResolver, file: DocumentFile, contents: String)
    fun getFileContents(file: File): String
    fun getFileContents(contentResolver: ContentResolver, file: DocumentFile): String
    fun getBinaryFileContents(contentResolver: ContentResolver, file: DocumentFile): ByteArray?
}
