package app.aaps.pump.carelevo.ext

import platform.Foundation.NSThread

internal actual fun sleepBlocking(ms: Long) = NSThread.sleepForTimeInterval(ms / 1000.0)
