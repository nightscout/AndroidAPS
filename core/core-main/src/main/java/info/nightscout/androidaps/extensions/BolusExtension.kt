package info.nightscout.androidaps.extensions

import info.nightscout.interfaces.data.Iob
import info.nightscout.androidaps.data.LocalInsulin
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Insulin
import info.nightscout.shared.utils.DateUtil
import info.nightscout.interfaces.utils.JsonHelper
import org.json.JSONObject

fun Bolus.iobCalc(activePlugin: ActivePlugin, time: Long, dia: Double): Iob {
    if (!isValid  || type == Bolus.Type.PRIMING ) return Iob()
    val insulinInterface: Insulin = activePlugin.activeInsulin
    return insulinInterface.iobCalcForTreatment(this, time, dia)
}

// Add specific calculation for Autotune (reference localInsulin for Peak/dia)
fun Bolus.iobCalc(time: Long, localInsulin: LocalInsulin): Iob {
    if (!isValid  || type == Bolus.Type.PRIMING ) return Iob()
    return localInsulin.iobCalcForTreatment(this, time)
}