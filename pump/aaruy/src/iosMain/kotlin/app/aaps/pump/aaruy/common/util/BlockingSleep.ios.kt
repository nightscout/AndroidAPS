package app.aaps.pump.aaruy.common.util

import platform.Foundation.NSThread

internal actual fun sleepBlocking(ms: Long) = NSThread.sleepForTimeInterval(ms / 1000.0)
