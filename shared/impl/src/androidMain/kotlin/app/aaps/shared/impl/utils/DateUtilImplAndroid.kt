package app.aaps.shared.impl.utils

import android.content.Context
import kotlin.time.Clock
import kotlin.time.Instant
import java.time.Clock as JavaClock

/**
 * Builds a [DateUtilImpl] the way Android callers always have, from a `Context`.
 *
 * [DateUtilImpl] itself is common code now and takes a [DateFormatPlatform], but every Android call
 * site and test still has a `Context` and nothing else. This keeps them working untouched: it reads
 * like the constructor it replaces, so the move to commonMain costs the app no edits at all.
 */
@Suppress("FunctionName")
fun DateUtilImpl(
    context: Context,
    clock: JavaClock = JavaClock.systemDefaultZone()
): DateUtilImpl = DateUtilImpl(AndroidDateFormatPlatform(context), clock.asKotlinClock())

/**
 * Views a `java.time.Clock` as a `kotlin.time.Clock`.
 *
 * The tests hand in fixed clocks to make time deterministic, and they build `java.time` ones. Rather
 * than rewrite those, the clock is adapted here.
 */
private fun JavaClock.asKotlinClock(): Clock = object : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis())
}
