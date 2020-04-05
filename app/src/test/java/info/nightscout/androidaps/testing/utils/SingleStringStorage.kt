package info.nightscout.androidaps.testing.utils

import info.nightscout.androidaps.utils.storage.Storage
import java.io.File

class SingleStringStorage : Storage {

    var contents: String = ""

    constructor(contents: String) {
        this.contents = contents
    }

    override fun getFileContents(file: File): String {
        return contents
    }

    override fun putFileContents(file: File, putContents: String) {
        contents = putContents
    }

    override fun toString(): String {
        return contents
    }

}