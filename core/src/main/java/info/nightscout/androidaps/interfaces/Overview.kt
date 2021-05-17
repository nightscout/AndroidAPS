package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.plugins.bus.RxBusWrapper

interface Overview : ConfigExportImport {

    fun refreshLoop(from: String)
    val overviewBus: RxBusWrapper
}