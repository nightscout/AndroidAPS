package info.nightscout.interfaces

import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.DeviceStatus
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.TherapyEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Send data to xDrip+ via Inter-app settings
 */
interface XDripBroadcast {

    /**
     *  Send calibration to xDrip+
     *  Accepting must be enabled in Inter-app settings - Accept Calibrations
     */
    fun sendCalibration(bg: Double): Boolean
    fun sendIn640gMode(glucoseValue: GlucoseValue)
    fun sendProfile(profileStoreJson: JSONObject)
    fun sendTreatments(addedOrUpdatedTreatments: JSONArray)
    fun sendSgvs(sgvs: JSONArray)

    /**
     *  Send data to xDrip+
     *  Accepting must be enabled in Inter-app settings - Accept Glucose
     */
    fun send(gv: GlucoseValue)
    /**
     *  Send data to xDrip+
     *  Accepting must be enabled in Inter-app settings - Accept treatments
     */
    fun send(bolus: Bolus)
    /**
     *  Send data to xDrip+
     *  Accepting must be enabled in Inter-app settings - Accept treatments
     */
    fun send(carbs: Carbs)
    /**
     *  Send data to xDrip+
     *  Accepting must be enabled in Inter-app settings - Accept treatments
     */
    fun send(tt: TemporaryTarget)
    /**
     *  Send data to xDrip+
     *  Accepting must be enabled in Inter-app settings - Accept treatments
     */
    fun send(te: TherapyEvent)
    /**
     *  Send data to xDrip+
     *  Accepting must be enabled in Inter-app settings - Accept treatments
     */
    fun send(deviceStatus: DeviceStatus)
    /**
     *  Send data to xDrip+
     *  Accepting must be enabled in Inter-app settings - Accept treatments
     */
    fun send(tb: TemporaryBasal)
    /**
     *  Send data to xDrip+
     *  Accepting must be enabled in Inter-app settings - Accept treatments
     */
    fun send(eb: ExtendedBolus)
    /**
     *  Send data to xDrip+
     *  Accepting must be enabled in Inter-app settings - Accept treatments
     */
    fun send(ps: ProfileSwitch)
    /**
     *  Send data to xDrip+
     *  Accepting must be enabled in Inter-app settings - Accept treatments
     */
    fun send(ps: EffectiveProfileSwitch)
    /**
     *  Send data to xDrip+
     *  Accepting must be enabled in Inter-app settings - Accept treatments
     */
    fun send(ps: OfflineEvent)
}