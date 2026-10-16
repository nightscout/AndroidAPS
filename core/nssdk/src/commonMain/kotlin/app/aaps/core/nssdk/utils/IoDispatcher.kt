package app.aaps.core.nssdk.utils

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The dispatcher network calls run on.
 *
 * `Dispatchers.IO` is not part of the common coroutines API - it exists on JVM and on Native, but not
 * as one shared declaration - so it is selected per platform here. The JVM actual is the same
 * `Dispatchers.IO` this client has always used, so nothing about threading changes on Android.
 */
internal expect val nsIoDispatcher: CoroutineDispatcher
