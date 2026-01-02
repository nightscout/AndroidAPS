package app.aaps.plugins.sync.wear.receivers

import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.utils.receivers.BundleLogger
import app.aaps.core.utils.receivers.DataWorkerStorage
import dagger.android.DaggerBroadcastReceiver
import javax.inject.Inject

open class WearDataReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var config: Config
    @Inject lateinit var rxBus: RxBus

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val bundle = intent.extras ?: return
        aapsLogger.debug(LTag.WEAR, "onReceive ${intent.action} ${BundleLogger.log(bundle)}")

        when (intent.action) {
            Intents.AAPS_CLIENT_WEAR_DATA -> {
                val client = bundle.getInt(CLIENT)
                val data = bundle.getString(DATA)
                if (client == 0 || data == null) {
                    aapsLogger.error(LTag.WEAR, "Misformatted data received. Ignoring.")
                    return
                }
                // Check for allowed configuration
                if (
                    config.AAPSCLIENT1 && client == 2 ||
                    (config.APS || config.PUMPCONTROL) && (client == 1 || client == 2)
                ) {
                    // Send to phone
                    val eventData = EventData.deserialize(data)
                    if (eventData is EventData.EventDataSet)
                        rxBus.send(EventMobileToWear(eventData))
                }
            }

            else                          -> null
        }
    }

    companion object {

        const val PERMISSION = "app.aaps.weardata.permission"
        const val CLIENT = "client"
        const val DATA = "data"
    }
}
