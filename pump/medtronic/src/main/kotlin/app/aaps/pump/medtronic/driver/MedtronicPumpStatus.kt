package app.aaps.pump.medtronic.driver

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.defs.PumpDeviceState
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.data.PumpStatus
import app.aaps.pump.common.events.EventRileyLinkDeviceStatusChange
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.data.RLHistoryItem
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import app.aaps.pump.common.sync.PumpDbEntryTBR
import app.aaps.pump.medtronic.defs.BasalProfileStatus
import app.aaps.pump.medtronic.defs.BatteryType
import app.aaps.pump.medtronic.defs.MedtronicDeviceType
import app.aaps.pump.medtronic.keys.MedtronicLongNonKey
import app.aaps.pump.medtronic.keys.MedtronicStringPreferenceKey
import java.util.Calendar
import java.util.GregorianCalendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by andy on 4/28/18.
 */
@Singleton
class MedtronicPumpStatus @Inject constructor(
    preferences: Preferences,
    private val rxBus: RxBus,
    private val rileyLinkUtil: RileyLinkUtil
) : PumpStatus(PumpType.MEDTRONIC_522_722) {

    var errorDescription: String? = null
    var serialNumber: String = preferences.get(MedtronicStringPreferenceKey.Serial)
    var maxBolus: Double? = null
    var maxBasal: Double? = null
    var runningTBR: PumpDbEntryTBR? = null
    var runningTBRWithTemp: PumpDbEntryTBR? = null

    // statuses
    var pumpDeviceState = PumpDeviceState.NeverContacted
        set(pumpDeviceState) {
            field = pumpDeviceState
            rileyLinkUtil.rileyLinkHistory.add(RLHistoryItem(pumpDeviceState, RileyLinkTargetDevice.MedtronicPump))
            rxBus.send(EventRileyLinkDeviceStatusChange(pumpDeviceState))
        }

    var medtronicDeviceType: MedtronicDeviceType = MedtronicDeviceType.Medtronic_522
    var medtronicPumpMap: MutableMap<String, PumpType> = mutableMapOf()
    var medtronicDeviceTypeMap: MutableMap<String, MedtronicDeviceType> = mutableMapOf()
    var basalProfileStatus = BasalProfileStatus.NotInitialized
    var batteryType = BatteryType.None

    init {
        activeProfileName = "STD"
        reservoirRemainingUnits = 75.0
        batteryRemaining = 75
        if (medtronicPumpMap.isEmpty()) createMedtronicPumpMap()
        if (medtronicDeviceTypeMap.isEmpty()) createMedtronicDeviceTypeMap()
        lastConnection = preferences.get(MedtronicLongNonKey.LastGoodPumpCommunicationTime)
        lastDataTime = lastConnection
    }

    private fun createMedtronicDeviceTypeMap() {
        medtronicDeviceTypeMap["512"] = MedtronicDeviceType.Medtronic_512
        medtronicDeviceTypeMap["712"] = MedtronicDeviceType.Medtronic_712
        medtronicDeviceTypeMap["515"] = MedtronicDeviceType.Medtronic_515
        medtronicDeviceTypeMap["715"] = MedtronicDeviceType.Medtronic_715
        medtronicDeviceTypeMap["522"] = MedtronicDeviceType.Medtronic_522
        medtronicDeviceTypeMap["722"] = MedtronicDeviceType.Medtronic_722
        medtronicDeviceTypeMap["523"] = MedtronicDeviceType.Medtronic_523_Revel
        medtronicDeviceTypeMap["723"] = MedtronicDeviceType.Medtronic_723_Revel
        medtronicDeviceTypeMap["554"] = MedtronicDeviceType.Medtronic_554_Veo
        medtronicDeviceTypeMap["754"] = MedtronicDeviceType.Medtronic_754_Veo
    }

    private fun createMedtronicPumpMap() {
        medtronicPumpMap = HashMap()
        medtronicPumpMap["512"] = PumpType.MEDTRONIC_512_712
        medtronicPumpMap["712"] = PumpType.MEDTRONIC_512_712
        medtronicPumpMap["515"] = PumpType.MEDTRONIC_515_715
        medtronicPumpMap["715"] = PumpType.MEDTRONIC_515_715
        medtronicPumpMap["522"] = PumpType.MEDTRONIC_522_722
        medtronicPumpMap["722"] = PumpType.MEDTRONIC_522_722
        medtronicPumpMap["523"] = PumpType.MEDTRONIC_523_723_REVEL
        medtronicPumpMap["723"] = PumpType.MEDTRONIC_523_723_REVEL
        medtronicPumpMap["554"] = PumpType.MEDTRONIC_554_754_VEO
        medtronicPumpMap["754"] = PumpType.MEDTRONIC_554_754_VEO
    }

    val basalProfileForHour: Double
        get() {
            if (basalsByHour != null) {
                val c = GregorianCalendar()
                val hour = c[Calendar.HOUR_OF_DAY]
                return basalsByHour!![hour]
            }
            return 0.0
        }

    override val errorInfo: String get() = errorDescription ?: "-"

    val tbrRemainingTime: Int?
        get() {
            if (tempBasalStart == null) return null
            if (tempBasalEnd == null) {
                val startTime = tempBasalStart!!
                tempBasalEnd = startTime + tempBasalLength!! * 60 * 1000
            }
            if (System.currentTimeMillis() > tempBasalEnd!!) {
                tempBasalStart = null
                tempBasalEnd = null
                tempBasalLength = null
                tempBasalAmount = null
                return null
            }
            val timeMinutes = (tempBasalEnd!! - System.currentTimeMillis()) / (1000 * 60)
            return timeMinutes.toInt()
        }
}