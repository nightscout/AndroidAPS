package info.nightscout.core.extensions

import info.nightscout.database.entities.Bolus
import info.nightscout.interfaces.insulin.Insulin
import info.nightscout.interfaces.iob.Iob
import info.nightscout.interfaces.plugin.ActivePlugin

fun Bolus.iobCalc(activePlugin: ActivePlugin, time: Long, dia: Double): Iob {
    if (!isValid  || type == Bolus.Type.PRIMING ) return Iob()
    val insulinInterface: Insulin = activePlugin.activeInsulin
    return insulinInterface.iobCalcForTreatment(this, time, dia)
}