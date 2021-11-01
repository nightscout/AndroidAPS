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
            Log.d("RUN","RUNRUNRUN")
            val cr = contentResolver.query(CONTENT_URI, null, null, null, null)
            val stringbuilder = StringBuilder()
            cr!!.moveToLast()
            val time = cr.getLong(0)
            val value = cr.getDouble(1) * 18.018 //value in mmol/l... transformed in mg/dl if value *18.018
            val readingCurrent = cr.getDouble(2)
            stringbuilder.append("$time   $value   $readingCurrent\n")
            Log.d("Readings", stringbuilder.toString())
            val intent = Intent()
            intent.action = "com.glunovoservice.BgEstimate"
            intent.putExtra("Time", time)
            intent.putExtra("BgEstimate", value)
            intent.putExtra("Current", readingCurrent)
            sendBroadcast(intent)
            val test = intent.action
            Log.d("Time", test!!)
            handler.postDelayed(this, 180000)
        }
    }

    companion object {
        const val AUTHORITY = "alexpr.co.uk.infinivocgm.cgm_db.CgmExternalProvider/"
        const val TABLE_NAME = "CgmReading"
        val CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + TABLE_NAME)
    }
}