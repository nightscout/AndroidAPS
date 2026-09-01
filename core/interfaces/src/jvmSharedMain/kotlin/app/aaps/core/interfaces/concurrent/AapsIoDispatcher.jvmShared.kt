package app.aaps.core.interfaces.concurrent

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Unchanged from before the multiplatform split. */
actual val aapsIoDispatcher: CoroutineDispatcher = Dispatchers.IO
