package app.aaps.core.main.extensions

import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.iob.Iob
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.database.entities.Bolus

fun Bolus.iobCalc(activePlugin: ActivePlugin, time: Long, dia: Double): Iob {
    if (!isValid || type == Bolus.Type.PRIMING) return Iob()
    val insulinInterface: Insulin = activePlugin.activeInsulin
    return insulinInterface.iobCalcForTreatment(this, time, dia)
}