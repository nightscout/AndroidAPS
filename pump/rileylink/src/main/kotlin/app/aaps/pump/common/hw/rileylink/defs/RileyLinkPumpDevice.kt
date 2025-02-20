package app.aaps.pump.common.hw.rileylink.defs

import app.aaps.pump.common.hw.rileylink.service.RileyLinkService

interface RileyLinkPumpDevice {

    fun setBusy(busy: Boolean)
    fun triggerPumpConfigurationChangedEvent()
    val rileyLinkService: RileyLinkService?
    val pumpInfo: RileyLinkPumpInfo
    val lastConnectionTimeMillis: Long
    fun setLastCommunicationToNow()
}