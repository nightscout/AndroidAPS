package app.aaps.implementation.maintenance

import app.aaps.core.interfaces.autotune.AutotuneFileStore
import app.aaps.core.interfaces.autotune.AutotuneFolder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * [AutotuneFileStore] on `java.io`, for Android and the desktop. Each says only where [directory] is.
 *
 * The file work is what Autotune did on Android before it moved to common code, kept as it was: a
 * folder is made with `mkdir`, a file is written with `println`, and the zip is deflated.
 */
abstract class JvmAutotuneFileStore : AutotuneFileStore {

    private fun folder(folder: AutotuneFolder) = File(directory, folder.folderName)

    override fun createFolder(folder: AutotuneFolder): Boolean {
        val path = folder(folder)
        if (path.exists() && path.isDirectory) return false
        // The base folder too: on the desktop it does not exist until something is written there.
        File(directory).mkdirs()
        path.mkdir()
        return true
    }

    override fun deleteFiles(folder: AutotuneFolder) {
        folder(folder).listFiles()?.forEach { file -> if (file.isFile) file.delete() }
    }

    override fun write(folder: AutotuneFolder, name: String, text: String): Boolean =
        try {
            val fw = FileWriter(File(folder(folder).absolutePath, name))
            val pw = PrintWriter(fw)
            pw.println(text)
            pw.close()
            fw.close()
            true
        } catch (_: IOException) {
            false
        }

    override fun zip(name: String): Boolean =
        try {
            val out = ZipOutputStream(BufferedOutputStream(FileOutputStream(File(directory, name))))
            for (folder in AutotuneFolder.entries) zipDirectory(folder(folder), folder.folderName, out)
            out.flush()
            out.close()
            true
        } catch (_: IOException) {
            false
        }

    private fun zipDirectory(folder: File, parentFolder: String, out: ZipOutputStream) {
        folder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                zipDirectory(file, parentFolder + "/" + file.name, out)
                return@forEach
            }
            try {
                out.putNextEntry(ZipEntry(parentFolder + "/" + file.name))
                BufferedInputStream(FileInputStream(file)).use { input ->
                    val bytesIn = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (input.read(bytesIn).also { read = it } != -1) out.write(bytesIn, 0, read)
                }
                out.closeEntry()
            } catch (_: IOException) {
            }
        }
    }

    private companion object {

        const val BUFFER_SIZE = 2048
    }
}
