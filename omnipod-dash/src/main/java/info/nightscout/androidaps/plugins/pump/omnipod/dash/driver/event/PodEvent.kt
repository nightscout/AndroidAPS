package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event

import java.io.Serializable

class PodEvent(
    val type: PodEventType,
    val data: Serializable?
)