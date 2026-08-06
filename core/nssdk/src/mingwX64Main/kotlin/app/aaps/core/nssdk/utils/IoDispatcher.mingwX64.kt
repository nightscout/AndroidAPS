package app.aaps.core.nssdk.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Native has no separate IO pool, so blocking work shares the default dispatcher.
 *
 * This target exists to prove commonMain carries no JVM API; an Apple target would use
 * `Dispatchers.IO` here, which Kotlin/Native does provide.
 */
internal actual val nsIoDispatcher: CoroutineDispatcher = Dispatchers.Default
