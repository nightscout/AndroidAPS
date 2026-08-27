package app.aaps.implementation.storage

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.storage.Storage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

// Metro builds this now; Dagger gets it through a @Provides delegate in `:app`. Scoped with Metro's
// @SingleIn, not javax @Singleton - the graph is generated in `:app`, which has no Dagger interop, so
// a javax scope there is ignored and every read would build a new one.
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
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