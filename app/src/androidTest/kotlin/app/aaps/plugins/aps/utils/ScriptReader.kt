package app.aaps.plugins.aps.utils

import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.IOException

class ScriptReader {

    @Throws(IOException::class)
    fun readFile(fileName: String): ByteArray {
        val assetManager = InstrumentationRegistry.getInstrumentation().context.assets
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