package info.nightscout.androidaps.testing.utils

import info.nightscout.androidaps.utils.storage.Storage
import java.io.File

class SingleStringStorage(var contents: String) : Storage {

    override fun getFileContents(file: File): String {
        return contents
    }

    override fun putFileContents(file: File, contents: String) {
        this.contents = contents
    }

    override fun toString(): String {
        return contents
    }

}