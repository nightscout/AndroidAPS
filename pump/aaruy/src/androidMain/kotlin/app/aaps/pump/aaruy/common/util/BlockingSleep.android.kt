package app.aaps.pump.aaruy.common.util

import android.os.SystemClock

internal actual fun sleepBlocking(ms: Long) = SystemClock.sleep(ms)
