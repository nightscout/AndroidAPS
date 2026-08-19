package app.aaps.core.interfaces.concurrent

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** Kotlin/Native provides a real IO pool on Apple targets, so blocking work does not share Default. */
actual val aapsIoDispatcher: CoroutineDispatcher = Dispatchers.IO
