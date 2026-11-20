package app.aaps.pump.ap550s.defs

import app.aaps.pump.ap550s.R


enum class TargetDevice(val resourceId: Int, val tuneUpEnabled: Boolean ) {
    Ap550s(R.string.target_device_AP550s, true),
    Other(R.string.target_device_other, false)
}