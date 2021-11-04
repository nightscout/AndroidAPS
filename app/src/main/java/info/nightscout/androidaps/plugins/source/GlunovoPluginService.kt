package info.nightscout.androidaps.plugins.source

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.Bundle
import android.os.Handler
import android.util.Log
import java.lang.StringBuilder

class GlunovoPluginService : Service() {
    private val handler = Handler()
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        handler.postDelayed(mgetValue, 180000)
        return super.onStartCommand(intent, flags, startId)
    }

    //180000
    private val mgetValue: Runnable = object : Runnable {
        override fun run() {
            val cr = contentResolver.query(CONTENT_URI, null, null, null, null)
            val stringbuilder = StringBuilder()
            cr!!.moveToLast()
            var i: Int =1
            while (i<=10) {
                cr!!.moveToPrevious()
                i = i + 1
            }
            var time = cr.getLong(0)
            var value = cr.getDouble(1) * 18.018 //value in mmol/l... transformed in mg/dl if value *18.018
            stringbuilder.append("$time   $value\n")

            var intent = Intent()
            intent.action = "com.glunovoservice.BgEstimate"
            intent.putExtra("Time", time)
            intent.putExtra("BgEstimate", value)
            intent.setPackage("info.nightscout.androidaps")
            var bundle = Bundle()
            bundle.putLong("Time",time);
            bundle.putDouble("BgEstimate",value);
            //bundle.putDouble("Current",readingCurrent);
            intent.putExtra("bundle", bundle);
            sendBroadcast(intent)

            while (i>0)
            {
                cr!!.moveToNext()
                time = cr.getLong(0)
                value = cr.getDouble(1) * 18.018 //value in mmol/l... transformed in mg/dl if value *18.018
                stringbuilder.append("$time   $value\n")
                intent = Intent()
                intent.action = "com.glunovoservice.BgEstimate"
                intent.putExtra("Time", time)
                intent.putExtra("BgEstimate", value)
                intent.setPackage("info.nightscout.androidaps")
                bundle = Bundle()
                bundle.putLong("Time",time);
                bundle.putDouble("BgEstimate",value);
                //bundle.putDouble("Current",readingCurrent);
                intent.putExtra("bundle", bundle);
                sendBroadcast(intent)
                i = i - 1
            }
            handler.postDelayed(this, 180000)
        }
    }

    companion object {
        const val AUTHORITY = "alexpr.co.uk.infinivocgm.cgm_db.CgmExternalProvider/"
        const val TABLE_NAME = "CgmReading"
        val CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + TABLE_NAME)
    }
}