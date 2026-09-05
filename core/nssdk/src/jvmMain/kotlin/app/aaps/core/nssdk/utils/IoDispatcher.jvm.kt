package app.aaps.core.nssdk.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Unchanged from before the multiplatform split. */
internal actual val nsIoDispatcher: CoroutineDispatcher = Dispatchers.IO
