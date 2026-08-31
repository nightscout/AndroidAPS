package app.aaps.pump.omnipod.eros.rileylink.service

import android.content.Intent
import android.content.res.Configuration
import android.os.Binder
import android.os.IBinder
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.defs.PumpDeviceState
import app.aaps.pump.common.hw.rileylink.RileyLinkCommunicationManager
import app.aaps.pump.common.hw.rileylink.RileyLinkConst
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkStringKey
import app.aaps.pump.common.hw.rileylink.service.RileyLinkService
import app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin
import app.aaps.pump.omnipod.eros.R
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import javax.inject.Inject

/**
 * Created by andy on 4.8.2019
 * RileyLinkOmnipodService is intended to stay running when the gui-app is closed.
 *
 * Was Java, and that mattered: Metro could not generate a `MembersInjector` for it - it crashed the
 * code generator - so the fields were filled by a hand written injector with no compiler check behind
 * it. As Kotlin it gets the generated one every other service uses, and a field added here can no
 * longer be silently left null.
 */
class RileyLinkOmnipodService : RileyLinkService() {

    private val mBinder: IBinder = LocalBinder()

    // Injected but not read here, exactly as in the Java version. Kept so this stays a translation and
    // nothing changes about what the graph builds when the service starts.
    @Inject lateinit var omnipodErosPumpPlugin: OmnipodErosPumpPlugin
    @Inject lateinit var aapsOmnipodUtil: AapsOmnipodUtil
    @Inject lateinit var omnipodRileyLinkCommunicationManager: OmnipodRileyLinkCommunicationManager

    private var rileyLinkAddressChanged = false
    private var inPreInit = true
    private var rileyLinkAddress: String? = null
    // Public getter, private setter: Kotlin callers read `service.errorDescription`, Java callers get
    // `getErrorDescription()`, and only this class can set it - as before.
    var errorDescription: String? = null
        private set

    override fun onConfigurationChanged(newConfig: Configuration) {
        aapsLogger.warn(LTag.PUMPBTCOMM, "onConfigurationChanged")
        super.onConfigurationChanged(newConfig)
    }

    override fun onBind(intent: Intent): IBinder = mBinder

    override val encoding: RileyLinkEncodingType
        get() = RileyLinkEncodingType.Manchester

    override fun initRileyLinkServiceData() {
        rileyLinkServiceData.targetDevice = RileyLinkTargetDevice.Omnipod
        rileyLinkServiceData.rileyLinkTargetFrequency = RileyLinkTargetFrequency.Omnipod

        rileyLinkServiceData.rileyLinkAddress = preferences.get(RileyLinkStringKey.MacAddress)
        rileyLinkServiceData.rileyLinkName = preferences.get(RileyLinkStringKey.Name)

        rfSpy.startReader()

        aapsLogger.debug(LTag.PUMPBTCOMM, "RileyLinkOmnipodService newly constructed")
    }

    override val deviceCommunicationManager: RileyLinkCommunicationManager<*>
        get() = omnipodRileyLinkCommunicationManager

    override fun setPumpDeviceState(pumpDeviceState: PumpDeviceState) {
        // Intentionally left blank
        // We don't use PumpDeviceState in the Omnipod driver
    }

    val isInitialized: Boolean
        get() = rileyLinkServiceData.rileyLinkServiceState.isReady()

    /* private functions */

    override fun verifyConfiguration(forceRileyLinkAddressRenewal: Boolean): Boolean =
        try {
            errorDescription = null

            val address = preferences.get(RileyLinkStringKey.MacAddress)

            if (address.isEmpty()) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "RileyLink address invalid: no address")
                errorDescription = rh.gs(R.string.omnipod_eros_error_riley_link_address_invalid)
                false
            } else {
                if (!address.matches(REGEX_MAC.toRegex())) {
                    errorDescription = rh.gs(R.string.omnipod_eros_error_riley_link_address_invalid)
                    aapsLogger.debug(LTag.PUMPBTCOMM, "RileyLink address invalid: $address")
                } else {
                    if (address != rileyLinkAddress) {
                        rileyLinkAddress = address
                        rileyLinkAddressChanged = true
                    }
                }

                rileyLinkServiceData.rileyLinkTargetFrequency = RileyLinkTargetFrequency.Omnipod

                reconfigureService(forceRileyLinkAddressRenewal)

                true
            }
        } catch (ex: Exception) {
            errorDescription = ex.message
            aapsLogger.error(LTag.PUMPBTCOMM, "Error on Verification: " + ex.message, ex)
            false
        }

    private fun reconfigureService(forceRileyLinkAddressRenewal: Boolean): Boolean {
        if (!inPreInit) {
            if (rileyLinkAddressChanged || forceRileyLinkAddressRenewal) {
                rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkNewAddressSet)
                rileyLinkAddressChanged = false
            }
        }
        return !rileyLinkAddressChanged
    }

    fun setNotInPreInit(): Boolean {
        inPreInit = false
        return reconfigureService(false)
    }

    inner class LocalBinder : Binder() {

        val serviceInstance: RileyLinkOmnipodService get() = this@RileyLinkOmnipodService
    }

    companion object {

        // `\\d` because Kotlin unescapes the string before the regex sees it, and `\$` because a bare
        // `$` starts a string template. Same pattern as the Java original, which needed neither.
        private const val REGEX_MAC = "([\\da-fA-F]{1,2}(?::|\$)){6}"
    }
}
