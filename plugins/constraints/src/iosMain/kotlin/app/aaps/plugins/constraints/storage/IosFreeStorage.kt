package app.aaps.plugins.constraints.storage

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.abs
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLVolumeAvailableCapacityForImportantUsageKey
import platform.Foundation.NSUserDomainMask

/**
 * The space left on the volume that holds the app's database.
 *
 * Android measures the data partition with `StatFs`. The iOS counterpart is the volume the app
 * container sits on, which is the same thing said in Apple's words.
 *
 * ## Why the "important usage" number and not the plain free size
 *
 * `NSFileSystemFreeSize` reports the bytes that are free right now.
 * [NSURLVolumeAvailableCapacityForImportantUsageKey] reports what the app could actually write,
 * because iOS will evict purgeable caches to make room - and a phone commonly holds gigabytes of
 * those. Against a 200 MB threshold the two can disagree completely: the plain figure can read under
 * the limit on a phone that would have taken the write without complaint.
 *
 * That matters because of what the answer is used for. [StorageConstraintPlugin] turns a low number
 * into `isClosedLoopAllowed = false` - it switches closed looping off. Reporting the smaller figure
 * is not the cautious choice, it is a false alarm that stops the loop on a healthy phone.
 *
 * The plain figure is read as well, and logged when the two differ by more than a tenth, so a real
 * disagreement is visible in `aaps.log` rather than invisible.
 *
 * ## When it cannot be measured
 *
 * Zero, which disables the closed loop, and an error in the log. Not knowing how much room the
 * database has is a fair reason not to run a closed loop, and a value invented here would be a
 * silent no-op in the one class whose whole job is to notice a full disk. In practice this API does
 * not fail for an app's own container; the realistic cause would be a wrong path, which is a bug and
 * should be loud.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosFreeStorage(
    private val aapsLogger: AAPSLogger
) : FreeStorage {

    @OptIn(ExperimentalForeignApi::class)
    override fun availableMb(): Long {
        val path = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String
        if (path == null) {
            aapsLogger.error(LTag.CONSTRAINTS, "No Application Support directory, so the free space is unknown")
            return 0
        }

        val importantBytes = memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            val values = NSURL.fileURLWithPath(path)
                .resourceValuesForKeys(listOf(NSURLVolumeAvailableCapacityForImportantUsageKey), errorPtr.ptr)
            if (values == null) {
                aapsLogger.error(LTag.CONSTRAINTS, "Cannot read the free space of $path: ${errorPtr.value?.localizedDescription}")
                return 0
            }
            (values[NSURLVolumeAvailableCapacityForImportantUsageKey] as? NSNumber)?.longLongValue
        }
        if (importantBytes == null) {
            aapsLogger.error(LTag.CONSTRAINTS, "The volume of $path reports no available capacity for important usage")
            return 0
        }

        warnIfPlainFigureDisagrees(path, importantBytes)
        return bytesToMb(importantBytes)
    }

    /** The plain free size, only to say so when it tells a very different story. */
    @OptIn(ExperimentalForeignApi::class)
    private fun warnIfPlainFigureDisagrees(path: String, importantBytes: Long) {
        val plain = (NSFileManager.defaultManager.attributesOfFileSystemForPath(path, null)
            ?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: return
        // A tenth: the two figures are never exactly equal, and only a real gap is worth a line.
        if (abs(plain - importantBytes) > importantBytes / 10)
            aapsLogger.debug(
                LTag.CONSTRAINTS,
                "Free space: ${bytesToMb(importantBytes)} MB for important usage, ${bytesToMb(plain)} MB plain free"
            )
    }
}

/**
 * Bytes as whole megabytes, rounded down.
 *
 * Separate and internal so it can be tested: the capacity itself comes from the device and is
 * different on every run, so only the conversion can be pinned to a value. Rounding down is
 * deliberate - it is the direction that never claims space the volume does not have.
 */
internal fun bytesToMb(bytes: Long): Long = bytes / BYTES_IN_MB

private const val BYTES_IN_MB = 1_048_576L
