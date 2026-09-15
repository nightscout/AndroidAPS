package app.aaps.core.data.time

import java.util.TimeZone

/** Exactly what the models did before, so the stored `utcOffset` does not change. */
actual fun systemUtcOffsetAt(timestamp: Long): Long =
    TimeZone.getDefault().getOffset(timestamp).toLong()
