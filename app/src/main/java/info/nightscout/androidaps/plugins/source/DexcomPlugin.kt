package info.nightscout.androidaps.plugins.source

import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.RequestDexcomPermissionActivity
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DexcomPlugin @Inject constructor(
    injector: HasAndroidInjector,
    private val sp: SP,
    private val mainApp: MainApp,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val nsUpload: NSUpload,
    config: Config
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_dexcom_g6)
    .pluginName(R.string.dexcom_app_patched)
    .shortName(R.string.dexcom_short)
    .preferencesId(R.xml.pref_bgsourcedexcom)
    .description(R.string.description_source_dexcom),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    init {
        if (!config.NSCLIENT) {
            pluginDescription.setDefault()
        }
    }

    override fun advancedFilteringSupported(): Boolean {
        return true
    }

    override fun onStart() {
        super.onStart()
        if (ContextCompat.checkSelfPermission(mainApp, PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(mainApp, RequestDexcomPermissionActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            mainApp.startActivity(intent)
        }
    }

    fun findDexcomPackageName(): String? {
        val packageManager = mainApp.packageManager
        for (packageInfo in packageManager.getInstalledPackages(0)) {
            if (PACKAGE_NAMES.contains(packageInfo.packageName)) return packageInfo.packageName
        }
        return null
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        try {
            val sensorType = intent.getStringExtra("sensorType") ?: ""
            val glucoseValues = intent.getBundleExtra("glucoseValues")
            for (i in 0 until glucoseValues.size()) {
                glucoseValues.getBundle(i.toString())?.let { glucoseValue ->
                    val bgReading = BgReading()
                    bgReading.value = glucoseValue.getInt("glucoseValue").toDouble()
                    bgReading.direction = glucoseValue.getString("trendArrow")
                    bgReading.date = glucoseValue.getLong("timestamp") * 1000
                    bgReading.raw = 0.0
                    if (MainApp.getDbHelper().createIfNotExists(bgReading, "Dexcom$sensorType")) {
                        if (sp.getBoolean(R.string.key_dexcomg5_nsupload, false)) {
                            nsUpload.uploadBg(bgReading, "AndroidAPS-Dexcom$sensorType")
                        }
                        if (sp.getBoolean(R.string.key_dexcomg5_xdripupload, false)) {
                            nsUpload.sendToXdrip(bgReading)
                        }
                    }
                }
            }
            val meters = intent.getBundleExtra("meters")
            for (i in 0 until meters.size()) {
                val meter = meters.getBundle(i.toString())
                meter?.let {
                    val timestamp = it.getLong("timestamp") * 1000
                    val now = DateUtil.now()
                    if (timestamp > now - T.months(1).msecs() && timestamp < now)
                        if (MainApp.getDbHelper().getCareportalEventFromTimestamp(timestamp) == null) {
                            val jsonObject = JSONObject()
                            jsonObject.put("enteredBy", "AndroidAPS-Dexcom$sensorType")
                            jsonObject.put("created_at", DateUtil.toISOString(timestamp))
                            jsonObject.put("eventType", CareportalEvent.BGCHECK)
                            jsonObject.put("glucoseType", "Finger")
                            jsonObject.put("glucose", meter.getInt("meterValue"))
                            jsonObject.put("units", Constants.MGDL)

                            val careportalEvent = CareportalEvent(injector)
                            careportalEvent.date = timestamp
                            careportalEvent.source = Source.USER
                            careportalEvent.eventType = CareportalEvent.BGCHECK
                            careportalEvent.json = jsonObject.toString()
                            MainApp.getDbHelper().createOrUpdate(careportalEvent)
                            nsUpload.uploadCareportalEntryToNS(jsonObject)
                        }
                }
            }
            if (sp.getBoolean(R.string.key_dexcom_lognssensorchange, false) && intent.hasExtra("sensorInsertionTime")) {
                intent.extras?.let {
                    val sensorInsertionTime = it.getLong("sensorInsertionTime") * 1000
                    val now = DateUtil.now()
                    if (sensorInsertionTime > now - T.months(1).msecs() && sensorInsertionTime < now)
                        if (MainApp.getDbHelper().getCareportalEventFromTimestamp(sensorInsertionTime) == null) {
                            val jsonObject = JSONObject()
                            jsonObject.put("enteredBy", "AndroidAPS-Dexcom$sensorType")
                            jsonObject.put("created_at", DateUtil.toISOString(sensorInsertionTime))
                            jsonObject.put("eventType", CareportalEvent.SENSORCHANGE)
                            val careportalEvent = CareportalEvent(injector)
                            careportalEvent.date = sensorInsertionTime
                            careportalEvent.source = Source.USER
                            careportalEvent.eventType = CareportalEvent.SENSORCHANGE
                            careportalEvent.json = jsonObject.toString()
                            MainApp.getDbHelper().createOrUpdate(careportalEvent)
                            nsUpload.uploadCareportalEntryToNS(jsonObject)
                        }
                }
            }
        } catch (e: Exception) {
            aapsLogger.error("Error while processing intent from Dexcom App", e)
        }
    }

    companion object {
        private val PACKAGE_NAMES = arrayOf("com.dexcom.cgm.region1.mgdl", "com.dexcom.cgm.region1.mmol",
            "com.dexcom.cgm.region2.mgdl", "com.dexcom.cgm.region2.mmol",
            "com.dexcom.g6.region1.mmol", "com.dexcom.g6.region2.mgdl",
            "com.dexcom.g6.region3.mgdl", "com.dexcom.g6.region3.mmol")
        const val PERMISSION = "com.dexcom.cgm.EXTERNAL_PERMISSION"
    }
}
