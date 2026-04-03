package app.aaps.core.interfaces.insulin

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.configuration.ConfigExportImport

interface Insulin : ConfigExportImport {

    val id: InsulinType
    val friendlyName: String

    /**
     * Provide Current Pump Insulin
     */
    val iCfg: ICfg
}