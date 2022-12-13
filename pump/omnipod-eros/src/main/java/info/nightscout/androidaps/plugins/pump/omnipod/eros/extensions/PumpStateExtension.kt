package info.nightscout.androidaps.plugins.pump.omnipod.eros.extensions

import info.nightscout.interfaces.pump.PumpSync
import kotlin.math.ceil
import kotlin.math.max

val PumpSync.PumpState.TemporaryBasal.plannedRemainingMinutesRoundedUp: Int
    get() = max(ceil((end - System.currentTimeMillis()) / 1000.0 / 60).toInt(), 0)



