package info.nightscout.androidaps.plugins.general.tidepool

import com.squareup.otto.Subscribe
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader
import info.nightscout.androidaps.plugins.general.tidepool.utils.RateLimit
import info.nightscout.androidaps.receivers.ChargingStateReceiver
import info.nightscout.androidaps.utils.SP
import org.slf4j.LoggerFactory

object TidepoolPlugin : PluginBase(PluginDescription()
        .mainType(PluginType.GENERAL)
        .pluginName(R.string.tidepool)
        .shortName(R.string.tidepool_shortname)
        .preferencesId(R.xml.pref_tidepool)
        .description(R.string.description_tidepool)
) {
    private val log = LoggerFactory.getLogger(L.TIDEPOOL)
    private var wifiConnected = false

    override fun onStart() {
        MainApp.bus().register(this)
        super.onStart()
    }

    override fun onStop() {
        MainApp.bus().unregister(this)
        super.onStop()
    }

    @Suppress("UNUSED_PARAMETER")
    @Subscribe
    fun onStatusEvent(ev: EventNewBG) {
        if (enabled()
                && (!SP.getBoolean(R.string.key_tidepool_only_while_charging, false) || ChargingStateReceiver.isCharging())
                && (!SP.getBoolean(R.string.key_tidepool_only_while_unmetered, false) || wifiConnected)
                && RateLimit.ratelimit("tidepool-new-data-upload", 1200))
            TidepoolUploader.doLogin()
    }

    @Subscribe
    fun onEventNetworkChange(ev: EventNetworkChange) {
        wifiConnected = ev.wifiConnected
    }

    fun enabled(): Boolean {
        return isEnabled(PluginType.GENERAL) && SP.getBoolean(R.string.key_cloud_storage_tidepool_enable, false)
    }

}