package info.nightscout.androidaps.plugins.source

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.joda.time.Seconds
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class GlunovoPluginService : Service() {
    private val backgroundExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        //backgroundExecutor.execute {mgetValue()}
        return super.onStartCommand(intent, flags, startId)
    }

    //180000
    fun mgetValue() {
        val cr = contentResolver.query(CONTENT_URI, null, null, null, null)
        val crfirst = contentResolver.query(CONTENT_URI, null, null, null, null)
        cr!!.moveToLast()
        crfirst!!.moveToFirst()
        var i = 1
        while ((i<=10) && (cr != crfirst)) {
            cr.moveToPrevious()
            i = i + 1
        }
        var time : Long = cr.getLong(0)
        var value : Double
        var intent : Intent
        var bundle : Bundle
        cr.moveToPrevious()

        while (i>=1)
        {
            cr.moveToNext()
            time = cr.getLong(0)
            value = cr.getDouble(1) * 18.018 //value in mmol/l... transformed in mg/dl if value *18.018
            intent = Intent()
            intent.action = "com.glunovoservice.BgEstimate"
            intent.putExtra("Time", time)
            intent.putExtra("BgEstimate", value)
            intent.setPackage("info.nightscout.androidaps")
            bundle = Bundle()
            bundle.putLong("Time",time)
            bundle.putDouble("BgEstimate",value)
            intent.putExtra("bundle", bundle)
            sendBroadcast(intent)
            i = i - 1
        }

        val curtime = System.currentTimeMillis()
            if (time != curtime) {
            cr.close()
            crfirst.close()
            var dur : Long = (curtime-time)
            backgroundExecutor.schedule({mgetValue()}, 180, TimeUnit.SECONDS)
        }
        else
        {
            cr.close()
            crfirst.close()
            backgroundExecutor.schedule({mgetValue()}, 180, TimeUnit.SECONDS)
        }
        backgroundExecutor.shutdown()
    }

    companion object {
        const val AUTHORITY = "alexpr.co.uk.infinivocgm.cgm_db.CgmExternalProvider/"
        const val TABLE_NAME = "CgmReading"
        val CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + TABLE_NAME)
    }
}