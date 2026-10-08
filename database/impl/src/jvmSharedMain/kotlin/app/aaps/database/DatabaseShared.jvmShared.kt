package app.aaps.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Database work is blocking, so it gets the IO pool on every JVM target. */
internal actual val databaseDispatcher: CoroutineDispatcher = Dispatchers.IO
