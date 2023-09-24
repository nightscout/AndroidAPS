package info.nightscout.androidaps.plugins.pump.medtronic.service

import android.content.Intent
import android.content.res.Configuration
import android.os.Binder
import android.os.IBinder
import app.aaps.core.interfaces.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkService
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.R
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUIComm
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.pump.common.defs.PumpDeviceState
import info.nightscout.pump.common.utils.ByteUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RileyLinkMedtronicService is intended to stay running when the gui-app is closed.
 */
@Singleton
class RileyLinkMedtronicService : RileyLinkService() {

    @Inject lateinit var medtronicPumpPlugin: MedtronicPumpPlugin
    @Inject lateinit var medtronicUtil: MedtronicUtil
    @Inject lateinit var medtronicPumpStatus: MedtronicPumpStatus
    @Inject lateinit var medtronicCommunicationManager: MedtronicCommunicationManager
    @Inject lateinit var medtronicUIComm: MedtronicUIComm

    private val mBinder: IBinder = LocalBinder()
    private var serialChanged = false
    private lateinit var frequencies: Array<String>
    private var rileyLinkAddress: String? = null
    private var rileyLinkAddressChanged = false
    private var encodingType: RileyLinkEncodingType? = null
    private var encodingChanged = false
    private var inPreInit = true

    override fun onCreate() {
        super.onCreate()
        aapsLogger.debug(LTag.PUMPCOMM, "RileyLinkMedtronicService newly created")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        aapsLogger.warn(LTag.PUMPCOMM, "onConfigurationChanged")
        super.onConfigurationChanged(newConfig)
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override val encoding: RileyLinkEncodingType
        get() = RileyLinkEncodingType.FourByteSixByteLocal

    /**
     * If you have customized RileyLinkServiceData you need to override this
     */
    override fun initRileyLinkServiceData() {
        frequencies = arrayOf(
            rh.gs(R.string.key_medtronic_pump_frequency_us_ca),
            rh.gs(R.string.key_medtronic_pump_frequency_worldwide)
        )
        // frequencies[0] = rh.gs(R.string.key_medtronic_pump_frequency_us_ca)
        // frequencies[1] = rh.gs(R.string.key_medtronic_pump_frequency_worldwide)
        rileyLinkServiceData.targetDevice = RileyLinkTargetDevice.MedtronicPump
        setPumpIDString(sp.getString(MedtronicConst.Prefs.PumpSerial, "000000"))

        // get most recently used RileyLink address and name
        rileyLinkServiceData.rileyLinkAddress = sp.getString(RileyLinkConst.Prefs.RileyLinkAddress, "")
        rileyLinkServiceData.rileyLinkName = sp.getString(RileyLinkConst.Prefs.RileyLinkName, "")
        rfSpy.startReader()
        aapsLogger.debug(LTag.PUMPCOMM, "RileyLinkMedtronicService newly constructed")
    }

    override val deviceCommunicationManager
        get() = medtronicCommunicationManager

    override fun setPumpDeviceState(pumpDeviceState: PumpDeviceState) {
        medtronicPumpStatus.pumpDeviceState = pumpDeviceState
    }

    private fun setPumpIDString(pumpID: String) {
        if (pumpID.length != 6) {
            aapsLogger.error("setPumpIDString: invalid pump id string: $pumpID")
            return
        }
        val pumpIDBytes = ByteUtil.fromHexString(pumpID)
        if (pumpIDBytes == null) {
            aapsLogger.error("Invalid pump ID? - PumpID is null.")
            rileyLinkServiceData.setPumpID("000000", byteArrayOf(0, 0, 0))
        } else if (pumpIDBytes.size != 3) {
            aapsLogger.error("Invalid pump ID? " + ByteUtil.shortHexString(pumpIDBytes))
            rileyLinkServiceData.setPumpID("000000", byteArrayOf(0, 0, 0))
        } else if (pumpID == "000000") {
            aapsLogger.error("Using pump ID $pumpID")
            rileyLinkServiceData.setPumpID(pumpID, byteArrayOf(0, 0, 0))
        } else {
            aapsLogger.info(LTag.PUMPBTCOMM, "Using pump ID $pumpID")
            val oldId = rileyLinkServiceData.pumpID
            rileyLinkServiceData.setPumpID(pumpID, pumpIDBytes)
            if (oldId != null && oldId != pumpID) {
                medtronicUtil.medtronicPumpModel = MedtronicDeviceType.Medtronic_522 // if we change pumpId, model probably changed too
                medtronicUtil.isModelSet = false
            }
            return
        }
        medtronicPumpStatus.pumpDeviceState = PumpDeviceState.InvalidConfiguration

        // LOG.info("setPumpIDString: saved pumpID " + idString);
    }

    inner class LocalBinder : Binder() {

        val serviceInstance: RileyLinkMedtronicService
            get() = this@RileyLinkMedtronicService
    }

    /* private functions */ // PumpInterface - REMOVE
    val isInitialized: Boolean
        get() = rileyLinkServiceData.rileyLinkServiceState.isReady

    override fun verifyConfiguration(forceRileyLinkAddressRenewal: Boolean): Boolean {
        return try {
            val regexSN = "[0-9]{6}"
            val regexMac = "([\\da-fA-F]{1,2}(?::|$)){6}"
            medtronicPumpStatus.errorDescription = "-"
            val serialNr = sp.getStringOrNull(MedtronicConst.Prefs.PumpSerial, null)
            if (serialNr == null) {
                medtronicPumpStatus.errorDescription = rh.gs(R.string.medtronic_error_serial_not_set)
                return false
            } else {
                if (!serialNr.matches(regexSN.toRegex())) {
                    medtronicPumpStatus.errorDescription = rh.gs(R.string.medtronic_error_serial_invalid)
                    return false
                } else {
                    if (serialNr != medtronicPumpStatus.serialNumber) {
                        medtronicPumpStatus.serialNumber = serialNr
                        serialChanged = true
                    }
                }
            }
            val pumpTypePref = sp.getStringOrNull(MedtronicConst.Prefs.PumpType, null)
            if (pumpTypePref == null) {
                medtronicPumpStatus.errorDescription = rh.gs(R.string.medtronic_error_pump_type_not_set)
                return false
            } else {
                val pumpTypePart = pumpTypePref.substring(0, 3)
                if (!pumpTypePart.matches("[0-9]{3}".toRegex())) {
                    medtronicPumpStatus.errorDescription = rh.gs(R.string.medtronic_error_pump_type_invalid)
                    return false
                } else {
                    val pumpType = medtronicPumpStatus.medtronicPumpMap[pumpTypePart] ?: return false
                    medtronicPumpStatus.medtronicDeviceType = medtronicPumpStatus.medtronicDeviceTypeMap[pumpTypePart] ?: return false
                    medtronicPumpStatus.pumpType = pumpType
                    medtronicPumpPlugin.pumpType = pumpType
                    if (pumpTypePart.startsWith("7")) medtronicPumpStatus.reservoirFullUnits = 300 else medtronicPumpStatus.reservoirFullUnits = 176
                }
            }
            val pumpFrequency = sp.getStringOrNull(MedtronicConst.Prefs.PumpFrequency, null)
            if (pumpFrequency == null) {
                medtronicPumpStatus.errorDescription = rh.gs(R.string.medtronic_error_pump_frequency_not_set)
                return false
            } else {
                if (pumpFrequency != frequencies[0] && pumpFrequency != frequencies[1]) {
                    medtronicPumpStatus.errorDescription = rh.gs(R.string.medtronic_error_pump_frequency_invalid)
                    return false
                } else {
                    medtronicPumpStatus.pumpFrequency = pumpFrequency
                    val isFrequencyUS = pumpFrequency == frequencies[0]
                    val newTargetFrequency = if (isFrequencyUS) //
                        RileyLinkTargetFrequency.MedtronicUS else RileyLinkTargetFrequency.MedtronicWorldWide
                    if (rileyLinkServiceData.rileyLinkTargetFrequency != newTargetFrequency) {
                        rileyLinkServiceData.rileyLinkTargetFrequency = newTargetFrequency
                    }
                }
            }
            val rileyLinkAddress = sp.getStringOrNull(RileyLinkConst.Prefs.RileyLinkAddress, null)
            if (rileyLinkAddress == null) {
                aapsLogger.debug(LTag.PUMP, "RileyLink address invalid: null")
                medtronicPumpStatus.errorDescription = rh.gs(R.string.medtronic_error_rileylink_address_invalid)
                return false
            } else {
                if (!rileyLinkAddress.matches(regexMac.toRegex())) {
                    medtronicPumpStatus.errorDescription = rh.gs(R.string.medtronic_error_rileylink_address_invalid)
                    aapsLogger.debug(LTag.PUMP, "RileyLink address invalid: %s", rileyLinkAddress)
                } else {
                    if (rileyLinkAddress != this.rileyLinkAddress) {
                        this.rileyLinkAddress = rileyLinkAddress
                        rileyLinkAddressChanged = true
                    }
                }
            }
            val maxBolusLcl = checkParameterValue(MedtronicConst.Prefs.MaxBolus, "25.0", 25.0)
            if (medtronicPumpStatus.maxBolus == null || medtronicPumpStatus.maxBolus != maxBolusLcl) {
                medtronicPumpStatus.maxBolus = maxBolusLcl

                //LOG.debug("Max Bolus from AAPS settings is " + maxBolus);
            }
            val maxBasalLcl = checkParameterValue(MedtronicConst.Prefs.MaxBasal, "35.0", 35.0)
            if (medtronicPumpStatus.maxBasal == null || medtronicPumpStatus.maxBasal != maxBasalLcl) {
                medtronicPumpStatus.maxBasal = maxBasalLcl

                //LOG.debug("Max Basal from AAPS settings is " + maxBasal);
            }
            val encodingTypeStr = sp.getStringOrNull(MedtronicConst.Prefs.Encoding, null)
                ?: return false
            val newEncodingType = RileyLinkEncodingType.getByDescription(encodingTypeStr, rh)
            if (encodingType == null) {
                encodingType = newEncodingType
            } else if (encodingType != newEncodingType) {
                encodingType = newEncodingType
                encodingChanged = true
            }
            val batteryTypeStr = sp.getStringOrNull(MedtronicConst.Prefs.BatteryType, null)
                ?: return false
            val batteryType = medtronicPumpStatus.getBatteryTypeByDescription(batteryTypeStr)
            if (medtronicPumpStatus.batteryType != batteryType) {
                medtronicPumpStatus.batteryType = batteryType
            }

            //String bolusDebugEnabled = sp.getStringOrNull(MedtronicConst.Prefs.BolusDebugEnabled, null);
            //boolean bolusDebug = bolusDebugEnabled != null && bolusDebugEnabled.equals(rh.gs(R.string.common_on));
            //MedtronicHistoryData.doubleBolusDebug = bolusDebug;
            rileyLinkServiceData.showBatteryLevel = sp.getBoolean(RileyLinkConst.Prefs.ShowBatteryLevel, false)
            reconfigureService(forceRileyLinkAddressRenewal)
            true
        } catch (ex: Exception) {
            medtronicPumpStatus.errorDescription = ex.message
            aapsLogger.error(LTag.PUMP, "Error on Verification: " + ex.message, ex)
            false
        }
    }

    private fun reconfigureService(forceRileyLinkAddressRenewal: Boolean): Boolean {
        if (!inPreInit) {
            if (serialChanged) {
                setPumpIDString(medtronicPumpStatus.serialNumber) // short operation
                serialChanged = false
            }
            if (rileyLinkAddressChanged || forceRileyLinkAddressRenewal) {
                rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkNewAddressSet, this)
                rileyLinkAddressChanged = false
            }
            if (encodingChanged) {
                changeRileyLinkEncoding(encodingType)
                encodingChanged = false
            }
        }

        // if (targetFrequencyChanged && !inPreInit && MedtronicUtil.getMedtronicService() != null) {
        // RileyLinkUtil.setRileyLinkTargetFrequency(targetFrequency);
        // // RileyLinkUtil.getRileyLinkCommunicationManager().refreshRileyLinkTargetFrequency();
        // targetFrequencyChanged = false;
        // }
        return !rileyLinkAddressChanged && !serialChanged && !encodingChanged // && !targetFrequencyChanged);
    }

    private fun checkParameterValue(key: Int, defaultValue: String, defaultValueDouble: Double): Double {
        var valueDouble: Double
        val value = sp.getString(key, defaultValue)
        valueDouble = try {
            value.toDouble()
        } catch (ex: Exception) {
            aapsLogger.error("Error parsing setting: %s, value found %s", key, value)
            defaultValueDouble
        }
        if (valueDouble > defaultValueDouble) {
            sp.putString(key, defaultValue)
            valueDouble = defaultValueDouble
        }
        return valueDouble
    }

    fun setNotInPreInit(): Boolean {
        inPreInit = false
        return reconfigureService(false)
    }
}