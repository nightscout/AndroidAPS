package info.nightscout.interfaces.storage

import java.io.File

// This may seems unnecessary abstraction - but it will simplify testing
interface Storage {

    fun getFileContents(file: File): String
    fun putFileContents(file: File, contents: String)

}
