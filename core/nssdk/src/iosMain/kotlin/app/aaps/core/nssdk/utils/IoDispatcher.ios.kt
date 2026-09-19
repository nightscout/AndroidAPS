package app.aaps.core.nssdk.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Kotlin/Native provides `Dispatchers.IO` on Apple targets, so blocking work gets its own pool here
 * rather than sharing the default one.
 */
internal actual val nsIoDispatcher: CoroutineDispatcher = Dispatchers.IO
