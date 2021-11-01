package info.nightscout.androidaps.plugins.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.interfaces.BgSource
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlunovoPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP
) : PluginBase(PluginDescription()
                   .mainType(PluginType.BGSOURCE)
                   .fragmentClass(BGSourceFragment::class.java.name)
                   .pluginIcon(R.drawable.ic_glunovo)
                   .pluginName(R.string.glunovo)
                   .preferencesId(R.xml.pref_bgsource)
                   .shortName(R.string.glunovo)
                   .description(R.string.description_source_glunovo),
               aapsLogger, resourceHelper, injector
), BgSource {

    // cannot be inner class because of needed injection
    class GlunovoWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var glunovoPlugin: GlunovoPlugin
        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var sp: SP
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var broadcastToXDrip: XDripBroadcast

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        @Suppress("SpellCheckingInspection")
        override fun doWork(): Result {
            var ret = Result.success()

            val AUTHORITY = "alexpr.co.uk.infinivocgm.cgm_db.CgmExternalProvider/"
            val TABLE_NAME = "CgmReading"
            val CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + TABLE_NAME)
            val context: Context = applicationContext
            val cr = context.contentResolver.query(CONTENT_URI, null, null, null, null)
            val stringbuilder = StringBuilder()
            stringbuilder.append("TEST OK")
            cr!!.moveToLast()
            val time = cr.getLong(0)
            val value = cr.getDouble(1) * 18.018 //value in mmol/l... transformed in mg/dl if value *18.018
            val readingCurrent = cr.getDouble(2)
            stringbuilder.append("$time   $value   $readingCurrent\n")
            Log.d("Readings", stringbuilder.toString())
            val intent = Intent()
            intent.action = "home.glunovoservice.BgEstimate"
            val bundle = Bundle()
            //intent.putExtra("Time", time)
            // intent.putExtra("BgEstimate", value)
            //intent.putExtra("Current", readingCurrent)
            return ret
        }

    }

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean =
        glucoseValue.sourceSensor == GlucoseValue.SourceSensor.LIBRE_1_TOMATO && sp.getBoolean(R.string.key_dexcomg5_nsupload, false)

    fun startGSer():Boolean = true
}
