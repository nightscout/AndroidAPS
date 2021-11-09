package info.nightscout.androidaps.plugins.source

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.services.Intents
import java.util.*

class GlunovoPluginService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        handler.postDelayed(mgetValue, 3000)
        return super.onStartCommand(intent, flags, startId)
    }
    fun start()
    {
        handler.postDelayed(mgetValue, 3000)
    }

    //180000
    private val mgetValue: Runnable = object : Runnable {
        override fun run() {
            val cr = contentResolver.query(CONTENT_URI, null, null, null, null)

            if (cr == null) { //check if cr has not data
                handler.postDelayed(this, 180000)
                return
            }
            cr.moveToLast()
            val curtime = Calendar.getInstance().timeInMillis
            val time : Long = cr.getLong(0)
            val value : Double = cr.getDouble(1) //value in mmol/l...
            val aapsLogger : AAPSLogger? = null
            if (time > curtime) {
                aapsLogger?.error(LTag.BGSOURCE, "Error in received data date/time "+ time.toString())
                return
            }

            if ((value < 2) || (value > 25)){
                aapsLogger?.error(LTag.BGSOURCE, "Error in received data value (value out of bounds) "+ value)
                return
            }

            val intent = Intent()
            val bundle = Bundle()
            intent.action = Intents.GLUNOVO_BG
            intent.setPackage("info.nightscout.androidaps")
            bundle.putLong("Time",time)
            bundle.putDouble("BgEstimate",value)
            intent.putExtra("bundle", bundle)
            sendBroadcast(intent)

            cr.close()
            handler.postDelayed(this, 180000)//-(curtime-time))
        }
    }

    companion object {
        const val AUTHORITY = "alexpr.co.uk.infinivocgm.cgm_db.CgmExternalProvider/"
        const val TABLE_NAME = "CgmReading"
        val CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + TABLE_NAME)
    }
}