package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.data.Iob
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.InsulinInterface

fun Bolus.iobCalc(activePlugin: ActivePluginProvider, time: Long, dia: Double): Iob {
    if (!isValid) return Iob()
    val insulinInterface: InsulinInterface = activePlugin.activeInsulin
    return insulinInterface.iobCalcForTreatment(this, time, dia)
}
