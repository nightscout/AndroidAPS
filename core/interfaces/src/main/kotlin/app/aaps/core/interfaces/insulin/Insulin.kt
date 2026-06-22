package app.aaps.core.interfaces.insulin

import app.aaps.core.data.model.ICfg

interface Insulin {

    val friendlyName: String

    /**
     * Provide Current Pump Insulin
     */
    val iCfg: ICfg
}