package app.aaps.plugins.dexcomoneplus

import android.os.SystemClock

internal actual fun onePlusElapsedMs(): Long = SystemClock.elapsedRealtime()
