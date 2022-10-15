package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.plugins.bus.RxBus

interface Overview : ConfigExportImport {

    val overviewBus: RxBus
}