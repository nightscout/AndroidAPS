package app.aaps.core.main.extensions

import app.aaps.core.data.db.BS
import app.aaps.core.data.iob.Iob
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.plugin.ActivePlugin

fun BS.iobCalc(activePlugin: ActivePlugin, time: Long, dia: Double): Iob {
    if (!isValid || type == BS.Type.PRIMING) return Iob()
    val insulinInterface: Insulin = activePlugin.activeInsulin
    return insulinInterface.iobCalcForTreatment(this, time, dia)
}