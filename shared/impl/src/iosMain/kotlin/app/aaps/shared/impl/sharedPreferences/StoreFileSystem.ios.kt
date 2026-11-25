package app.aaps.shared.impl.sharedPreferences

import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import platform.posix.F_BARRIERFSYNC
import platform.posix.O_RDONLY
import platform.posix.close
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.fsync
import platform.posix.open

internal actual val storeFileSystem: FileSystem = SyncingFileSystem(FileSystem.SYSTEM)

/**
 * [FileSystem.SYSTEM], but a file is synced before it is moved.
 *
 * DataStore writes the new content to a `.tmp` file, flushes it and moves it over the store. On iOS okio's
 * flush is only `fflush`, so nothing reaches the flash. After a forced restart or a kernel panic, the moved
 * file could be there without its data - the same damage as issue #5562 on Android.
 *
 * `F_BARRIERFSYNC`, not `F_FULLFSYNC`. The barrier makes sure that the data reaches the flash before the
 * move does, and that is all the store needs: losing the last save is fine, half a file is not.
 * `F_FULLFSYNC` also waits for the data and empties the drive cache each time, which is slow when the
 * settings are saved several times a minute. A plain `fsync` is the fallback for a file system that does
 * not know the barrier.
 */
@OptIn(ExperimentalForeignApi::class)
internal class SyncingFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {

    override fun atomicMove(source: Path, target: Path) {
        sync(source)
        super.atomicMove(source, target)
    }

    /** A sync works on the file, not on the handle that wrote it, so a handle of its own is enough. */
    private fun sync(file: Path) {
        val fd = open(file.toString(), O_RDONLY)
        if (fd == -1) throw IOException("Cannot open $file to sync it (errno $errno)")
        try {
            if (fcntl(fd, F_BARRIERFSYNC) == -1 && fsync(fd) == -1) throw IOException("Cannot sync $file (errno $errno)")
        } finally {
            close(fd)
        }
    }
}
