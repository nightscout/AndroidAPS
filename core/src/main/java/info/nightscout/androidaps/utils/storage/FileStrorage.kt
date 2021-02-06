package info.nightscout.androidaps.utils.storage

import java.io.File
import javax.inject.Singleton

@Singleton
class FileStorage : Storage {

    override fun getFileContents(file: File): String {
        return file.readText()
    }

    override fun putFileContents(file: File, contents: String) {
        file.writeText(contents)
    }

}