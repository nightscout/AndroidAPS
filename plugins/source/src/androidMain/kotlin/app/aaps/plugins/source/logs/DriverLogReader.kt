package app.aaps.plugins.source.logs

import app.aaps.plugins.dexcomoneplus.OnePlusLogMarkers
import java.io.File
import java.io.RandomAccessFile

/**
 * Reads the driver lines back out of the AAPS log file.
 *
 * AAPS writes one rolling file, `AndroidAPS.log`, in the folder that `logback.xml` names. Each
 * line looks like `10:23:45.123 [main] I/DEXCOM_ONEPLUS: DEXCOM_ONEPLUS_SESSION: …`, so the driver a
 * line belongs to is the name between the level and the colon.
 *
 * Two rules shape this class:
 *
 * - The file can be five megabytes. It is never read whole. Only the last [TAIL_BYTES] are read,
 *   and only the last [MAX_LINES] kept lines are handed back, so the screen stays cheap to open
 *   even while a session is writing.
 * - A failure line written with a cause carries its stack trace on the lines that follow, and
 *   those lines have no prefix of their own. They are kept with the line they belong to, because
 *   a stack trace with its first line missing is the one thing nobody can read.
 */
object DriverLogReader {

    /** The name of the file that is being written right now. Older ones are zipped. */
    const val CURRENT_FILE = "AndroidAPS.log"

    /** How much of the end of the file to read. */
    const val TAIL_BYTES = 512L * 1024L

    /** How many lines to keep, newest last. */
    const val MAX_LINES = 1000

    /** The logger names of the Dexcom ONE+ / G7 driver lines. */
    val DRIVER_LOGGERS: Set<String> = setOf(OnePlusLogMarkers.TAG)

    private val LINE_START = Regex("""^\d{2}:\d{2}:\d{2}\.\d{3} \[[^]]*] [A-Z]/([^:]+): """)

    /**
     * @param directory the folder `logback.xml` writes to, from `LoggerUtils.logDirectory`.
     * @return the kept lines, oldest first. An empty list when the file is not there yet, which is
     *   the normal answer on a build that has just started.
     */
    fun read(directory: String): List<String> {
        val file = File(directory, CURRENT_FILE)
        if (!file.isFile) return emptyList()
        return keep(tail(file, TAIL_BYTES), DRIVER_LOGGERS, MAX_LINES)
    }

    /**
     * Reads the last bytes of a file as lines.
     *
     * The first line of the block is dropped, because reading from a byte offset almost always
     * lands in the middle of a line and half a line is worse than no line. Nothing is dropped when
     * the whole file fits in the block.
     */
    fun tail(file: File, maxBytes: Long): List<String> {
        val length = file.length()
        val from = if (length > maxBytes) length - maxBytes else 0L
        val block = RandomAccessFile(file, "r").use { handle ->
            handle.seek(from)
            val bytes = ByteArray((length - from).toInt())
            handle.readFully(bytes)
            String(bytes, Charsets.UTF_8)
        }
        val lines = block.split("\n")
        return if (from > 0L) lines.drop(1) else lines
    }

    /**
     * Keeps the lines of the wanted drivers, and the lines that carry on from them.
     *
     * @param loggerNames the logger names to keep, for example `DEXCOM_ONEPLUS`.
     * @param maxLines how many lines to hand back at most, counted from the end.
     */
    fun keep(lines: List<String>, loggerNames: Set<String>, maxLines: Int): List<String> {
        val kept = ArrayList<String>()
        var carrying = false
        for (line in lines) {
            val match = LINE_START.find(line)
            if (match != null) {
                carrying = match.groupValues[1] in loggerNames
                if (carrying) kept.add(line)
            } else if (carrying && line.isNotBlank()) {
                kept.add(line)
            }
        }
        return if (kept.size > maxLines) kept.subList(kept.size - maxLines, kept.size).toList() else kept
    }
}
