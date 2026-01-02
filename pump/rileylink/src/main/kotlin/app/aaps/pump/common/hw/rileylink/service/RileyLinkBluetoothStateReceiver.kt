package app.aaps.pump.common.hw.rileylink.service

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.common.hw.rileylink.RileyLinkConst
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import dagger.android.DaggerBroadcastReceiver
import javax.inject.Inject

class RileyLinkBluetoothStateReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rileyLinkUtil: RileyLinkUtil

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action != null) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            when (state) {
                BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_TURNING_ON -> {
                }

                BluetoothAdapter.STATE_ON                                                                         -> {
                    aapsLogger.debug("RileyLinkBluetoothStateReceiver: Bluetooth back on. Sending broadcast to RileyLink Framework")
                    rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.BluetoothReconnected)
                }
            }
        }
    }

    fun unregisterBroadcasts(context: Context) {
        context.unregisterReceiver(this)
    }

    fun registerBroadcasts(context: Context) {
        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(this, filter)
    }
}