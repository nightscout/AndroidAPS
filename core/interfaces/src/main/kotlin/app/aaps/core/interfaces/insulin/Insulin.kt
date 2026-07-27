package app.aaps.core.interfaces.insulin

import app.aaps.core.data.model.ICfg

interface Insulin {

    /**
     * Provide Current Pump Insulin
     */
    val iCfg: ICfg
}