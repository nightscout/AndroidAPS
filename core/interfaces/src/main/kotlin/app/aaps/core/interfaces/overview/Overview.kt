package app.aaps.core.interfaces.overview

import app.aaps.core.interfaces.configuration.ConfigExportImport
import app.aaps.core.interfaces.rx.bus.RxBus

interface Overview : ConfigExportImport {

    val overviewBus: RxBus
}