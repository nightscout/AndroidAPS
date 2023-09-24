package app.aaps.core.main.extensions

import app.aaps.interfaces.insulin.Insulin
import app.aaps.interfaces.iob.Iob
import app.aaps.interfaces.plugin.ActivePlugin
import info.nightscout.database.entities.Bolus

fun Bolus.iobCalc(activePlugin: ActivePlugin, time: Long, dia: Double): Iob {
    if (!isValid || type == Bolus.Type.PRIMING) return Iob()
    val insulinInterface: Insulin = activePlugin.activeInsulin
    return insulinInterface.iobCalcForTreatment(this, time, dia)
}