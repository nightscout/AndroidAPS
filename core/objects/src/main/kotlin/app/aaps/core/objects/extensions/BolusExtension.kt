package app.aaps.core.objects.extensions

import app.aaps.core.data.iob.Iob
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.plugin.ActivePlugin

fun BS.iobCalc(activePlugin: ActivePlugin, time: Long, iCfg: ICfg): Iob {
    if (!isValid || type == BS.Type.PRIMING) return Iob()
    val insulinInterface: Insulin = activePlugin.activeInsulin
    return insulinInterface.iobCalcForTreatment(this, time, iCfg)
}