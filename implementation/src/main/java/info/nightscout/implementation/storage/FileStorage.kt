package info.nightscout.implementation.storage

import info.nightscout.interfaces.storage.Storage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorage @Inject constructor(): Storage {

    override fun getFileContents(file: File): String {
        return file.readText()
    }

    override fun putFileContents(file: File, contents: String) {
        file.writeText(contents)
    }

}