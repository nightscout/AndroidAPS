package app.aaps.core.interfaces.concurrent

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The dispatcher for blocking work - database reads, file access, long calculations.
 *
 * `Dispatchers.IO` is not one shared declaration in the common coroutines API. It exists on Android
 * and on Apple targets, but reaching it from common code needs this indirection. The Android actual
 * is the same `Dispatchers.IO` as before, so threading there is unchanged.
 *
 * Use this instead of `Dispatchers.IO` in any code that might become multiplatform. `Dispatchers.IO`
 * in `commonMain` does not fail with a helpful message - it reports the property as `internal`.
 */
expect val aapsIoDispatcher: CoroutineDispatcher
