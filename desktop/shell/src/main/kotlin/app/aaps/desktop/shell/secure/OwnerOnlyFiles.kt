package app.aaps.desktop.shell.secure

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/**
 * Files and folders only their owner can read.
 *
 * POSIX permissions where the file system has them (Linux, macOS). Windows has no POSIX permissions;
 * there the user's profile folder is already closed to other accounts by its ACL, and `~/.aaps` lives
 * inside it, so nothing is done.
 */
internal object OwnerOnlyFiles {

    private val OWNER_FILE = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
    private val OWNER_DIR = OWNER_FILE + PosixFilePermission.OWNER_EXECUTE

    /** Makes [dir] and closes it to other accounts (700). Safe to call on every start. */
    fun restrictDir(dir: File) {
        dir.mkdirs()
        runCatching { Files.setPosixFilePermissions(dir.toPath(), OWNER_DIR) }
    }

    /**
     * Writes [bytes] to [file] so that no other account can ever read them.
     *
     * The permissions are set when the file is created, not afterwards: a file that is created first
     * and restricted second is readable by everyone for that moment. The content goes to a sibling file
     * that is then moved over the real one, so a crash cannot leave half a key.
     */
    fun write(file: File, bytes: ByteArray) {
        file.parentFile?.let { restrictDir(it) }
        val temp = File(file.parentFile, "${file.name}.new").toPath()
        Files.deleteIfExists(temp)
        val created = runCatching { Files.createFile(temp, PosixFilePermissions.asFileAttribute(OWNER_FILE)) }
        if (created.isFailure) Files.createFile(temp)
        Files.write(temp, bytes)
        try {
            Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
