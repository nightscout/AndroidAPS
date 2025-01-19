package app.aaps.pump.common.hw.rileylink

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6b
import app.aaps.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6bGeoff
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType
import app.aaps.pump.common.hw.rileylink.data.RLHistoryItem
import java.util.ArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by andy on 17/05/2018.
 */
@Singleton
class RileyLinkUtil @Inject constructor(
    private val aapsLogger: AAPSLogger
) {

    val rileyLinkHistory: MutableList<RLHistoryItem> = ArrayList<RLHistoryItem>()
    var encoding: RileyLinkEncodingType? = null
        set(value) {
            field = value
            if (encoding == RileyLinkEncodingType.FourByteSixByteLocal)
                encoding4b6b = Encoding4b6bGeoff(aapsLogger)
        }

    var encoding4b6b: Encoding4b6b = Encoding4b6bGeoff(aapsLogger)

    fun sendBroadcastMessage(message: String?, context: Context) {
        val intent = Intent(message)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
}
