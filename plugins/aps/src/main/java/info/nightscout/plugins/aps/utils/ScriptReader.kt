package info.nightscout.plugins.aps.utils

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException

class ScriptReader(private val context: Context) {

    @Throws(IOException::class)
    fun readFile(fileName: String): ByteArray {
        val assetManager = context.assets
        val `is` = assetManager.open(fileName)
        val buffer = ByteArrayOutputStream()
        var nRead: Int
        val data = ByteArray(16384)
        while (`is`.read(data, 0, data.size).also { nRead = it } != -1) {
            buffer.write(data, 0, nRead)
        }
        buffer.flush()
        val bytes = buffer.toByteArray()
        `is`.close()
        buffer.close()
        return bytes
    }
}