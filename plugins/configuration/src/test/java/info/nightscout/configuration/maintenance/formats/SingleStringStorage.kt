package info.nightscout.configuration.maintenance.formats

import info.nightscout.interfaces.storage.Storage
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