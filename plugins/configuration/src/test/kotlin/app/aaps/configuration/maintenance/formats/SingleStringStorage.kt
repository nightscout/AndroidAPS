package app.aaps.configuration.maintenance.formats

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.storage.Storage
import java.io.File

class SingleStringStorage(var contents: String) : Storage {

    override fun getFileContents(file: File): String = contents
    override fun getFileContents(contentResolver: ContentResolver, file: DocumentFile): String = contents
    override fun getBinaryFileContents(contentResolver: ContentResolver, file: DocumentFile): ByteArray? = contents.encodeToByteArray()

    override fun putFileContents(file: File, contents: String) {
        this.contents = contents
    }

    override fun putFileContents(contentResolver: ContentResolver, file: DocumentFile, contents: String) {
        this.contents = contents
    }

    override fun toString(): String {
        return contents
    }

}