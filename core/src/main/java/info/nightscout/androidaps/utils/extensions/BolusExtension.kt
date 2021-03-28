package info.nightscout.androidaps.utils.extensions

import com.google.gson.Gson
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Iob
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.InsulinInterface
import info.nightscout.androidaps.utils.DateUtil
import org.json.JSONException
import org.json.JSONObject

fun Bolus.iobCalc(activePlugin: ActivePluginProvider, time: Long, dia: Double): Iob {
    if (!isValid) return Iob()
    val insulinInterface: InsulinInterface = activePlugin.activeInsulin
    return insulinInterface.iobCalcForTreatment(this, time, dia)
}

fun Bolus.toJson(): JSONObject =
    JSONObject()
        .put("eventType", if (type == Bolus.Type.SMB) TherapyEvent.Type.CORRECTION_BOLUS else TherapyEvent.Type.MEAL_BOLUS)
        .put("insulin", amount)
        .put("created_at", DateUtil.toISOString(timestamp))
        .put("date", timestamp)
        .put("isSMB", type == Bolus.Type.SMB).also {
            if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
            if (interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }

