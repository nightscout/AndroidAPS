package info.nightscout.plugins.sync.xdrip

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.toStringShort
import info.nightscout.core.iob.generateCOBString
import info.nightscout.core.iob.round
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.DeviceStatus
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.XDripBroadcast
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.receivers.Intents
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.plugins.sync.R
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAppInitialized
import info.nightscout.rx.events.EventAutosensCalculationFinished
import info.nightscout.rx.events.EventNewHistoryData
import info.nightscout.rx.events.EventRefreshOverview
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.extensions.safeQueryBroadcastReceivers
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XdripPlugin @Inject constructor(
    injector: HasAndroidInjector,
    private val sp: SP,
    private val profileFunction: ProfileFunction,
    rh: ResourceHelper,
    private val aapsSchedulers: AapsSchedulers,
    private val context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val loop: Loop,
    private val iobCobCalculator: IobCobCalculator,
    private val rxBus: RxBus,
    aapsLogger: AAPSLogger
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .pluginIcon((info.nightscout.core.main.R.drawable.ic_blooddrop_48))
        .pluginName(R.string.xdrip)
        .shortName(R.string.xdrip_shortname)
        .neverVisible(true)
        .preferencesId(R.xml.pref_xdrip)
        .description(R.string.description_xdrip),
    aapsLogger, rh, injector
), XDripBroadcast {

    private val disposable = CompositeDisposable()
    private var lastLoopStatus = false

    override fun onStart() {
        super.onStart()
        disposable += rxBus.toObservable(EventRefreshOverview::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ if (lastLoopStatus != loop.isEnabled()) sendStatusLine() }, fabricPrivacy::logException)
        disposable += rxBus.toObservable(EventNewHistoryData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendStatusLine() }, fabricPrivacy::logException)
        disposable += rxBus.toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendStatusLine() }, fabricPrivacy::logException)
        disposable += rxBus.toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendStatusLine() }, fabricPrivacy::logException)
    }

    override fun onStop() {
        super.onStop()
        disposable.clear()
        sendStatusLine()
    }

    private fun sendStatusLine() {
        if (sp.getBoolean(R.string.key_xdrip_send_status, false)) {
            val status = profileFunction.getProfile()?.let { buildStatusLine(it) } ?: ""
            context.sendBroadcast(
                Intent(Intents.ACTION_NEW_EXTERNAL_STATUSLINE).also {
                    it.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    it.putExtras(Bundle().apply { putString(Intents.EXTRA_STATUSLINE, status) })
                }
            )
        }
    }

    private fun buildStatusLine(profile: Profile): String {
        val status = StringBuilder()
        @Suppress("LiftReturnOrAssignment")
        if (!loop.isEnabled()) {
            status.append(rh.gs(R.string.disabled_loop)).append("\n")
            lastLoopStatus = false
        } else lastLoopStatus = true

        //Temp basal
        iobCobCalculator.getTempBasalIncludingConvertedExtended(System.currentTimeMillis())?.let {
            status.append(it.toStringShort()).append(" ")
        }
        //IOB
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        status.append(DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob)).append(rh.gs(info.nightscout.core.ui.R.string.insulin_unit_shortname))
        if (sp.getBoolean(R.string.key_xdrip_status_detailed_iob, true))
            status.append("(")
                .append(DecimalFormatter.to2Decimal(bolusIob.iob))
                .append("|")
                .append(DecimalFormatter.to2Decimal(basalIob.basaliob))
                .append(")")
        if (sp.getBoolean(R.string.key_xdrip_status_show_bgi, true)) {
            val bgi = -(bolusIob.activity + basalIob.activity) * 5 * Profile.fromMgdlToUnits(profile.getIsfMgdl(), profileFunction.getUnits())
            status.append(" ")
                .append(if (bgi >= 0) "+" else "")
                .append(DecimalFormatter.to2Decimal(bgi))
        }
        // COB
        status.append(" ").append(iobCobCalculator.getCobInfo("StatusLinePlugin").generateCOBString())
        return status.toString()
    }

    override fun sendCalibration(bg: Double): Boolean {
        val bundle = Bundle()
        bundle.putDouble("glucose_number", bg)
        bundle.putString("units", if (profileFunction.getUnits() == GlucoseUnit.MGDL) "mgdl" else "mmol")
        bundle.putLong("timestamp", System.currentTimeMillis())
        val intent = Intent(Intents.ACTION_REMOTE_CALIBRATION)
        intent.putExtras(bundle)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        context.sendBroadcast(intent)
        val q = context.packageManager.safeQueryBroadcastReceivers(intent, 0)
        return if (q.isEmpty()) {
            ToastUtils.errorToast(context, R.string.xdrip_not_installed)
            aapsLogger.debug(rh.gs(R.string.xdrip_not_installed))
            false
        } else {
            ToastUtils.errorToast(context, R.string.calibration_sent)
            aapsLogger.debug(rh.gs(R.string.calibration_sent))
            true
        }
    }

    // sent in 640G mode
    // com.eveningoutpost.dexdrip.NSEmulatorReceiver
    override fun sendIn640gMode(glucoseValue: GlucoseValue) {
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_dexcomg5_xdripupload, false)) {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            try {
                val entriesBody = JSONArray()
                val json = JSONObject()
                json.put("sgv", glucoseValue.value)
                json.put("direction", glucoseValue.trendArrow.text)
                json.put("device", "G5")
                json.put("type", "sgv")
                json.put("date", glucoseValue.timestamp)
                json.put("dateString", format.format(glucoseValue.timestamp))
                entriesBody.put(json)
                val bundle = Bundle()
                bundle.putString("action", "add")
                bundle.putString("collection", "entries")
                bundle.putString("data", entriesBody.toString())
                val intent = Intent(Intents.XDRIP_PLUS_NS_EMULATOR)
                intent.putExtras(bundle).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                context.sendBroadcast(intent)
                val receivers = context.packageManager.safeQueryBroadcastReceivers(intent, 0)
                if (receivers.isEmpty()) {
                    //NSUpload.log.debug("No xDrip receivers found. ")
                    aapsLogger.debug(LTag.BGSOURCE, "No xDrip receivers found.")
                } else {
                    aapsLogger.debug(LTag.BGSOURCE, "${receivers.size} xDrip receivers")
                }
            } catch (e: JSONException) {
                aapsLogger.error(LTag.BGSOURCE, "Unhandled exception", e)
            }
        }
    }

    // sent in NSClient dbaccess mode
    override fun sendProfile(profileStoreJson: JSONObject) {
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_nsclient_localbroadcasts, false))
            broadcast(
                Intent(Intents.ACTION_NEW_PROFILE).apply {
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtras(Bundle().apply { putString("profile", profileStoreJson.toString()) })
                }
            )

    }

    // sent in NSClient dbaccess mode
    override fun sendTreatments(addedOrUpdatedTreatments: JSONArray) {
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_nsclient_localbroadcasts, false))
            splitArray(addedOrUpdatedTreatments).forEach { part ->
                broadcast(
                    Intent(Intents.ACTION_NEW_TREATMENT).apply {
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        putExtras(Bundle().apply { putString("treatments", part.toString()) })
                    }
                )
            }
    }

    // sent in NSClient dbaccess mode
    override fun sendSgvs(sgvs: JSONArray) {
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_nsclient_localbroadcasts, false))
            splitArray(sgvs).forEach { part ->
                broadcast(
                    Intent(Intents.ACTION_NEW_SGV).apply {
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        putExtras(Bundle().apply { putString("sgvs", part.toString()) })
                    }
                )
            }
    }

    override fun send(gv: GlucoseValue) {
        TODO("Not yet implemented")
    }

    override fun send(bolus: Bolus) {
        TODO("Not yet implemented")
    }

    override fun send(carbs: Carbs) {
        TODO("Not yet implemented")
    }

    override fun send(tt: TemporaryTarget) {
        TODO("Not yet implemented")
    }

    override fun send(te: TherapyEvent) {
        TODO("Not yet implemented")
    }

    override fun send(deviceStatus: DeviceStatus) {
        TODO("Not yet implemented")
    }

    override fun send(tb: TemporaryBasal) {
        TODO("Not yet implemented")
    }

    override fun send(eb: ExtendedBolus) {
        TODO("Not yet implemented")
    }

    override fun send(ps: ProfileSwitch) {
        TODO("Not yet implemented")
    }

    override fun send(ps: EffectiveProfileSwitch) {
        TODO("Not yet implemented")
    }

    override fun send(ps: OfflineEvent) {
        TODO("Not yet implemented")
    }

    private fun splitArray(array: JSONArray): List<JSONArray> {
        var ret: MutableList<JSONArray> = ArrayList()
        try {
            val size = array.length()
            var count = 0
            var newarr: JSONArray? = null
            for (i in 0 until size) {
                if (count == 0) {
                    if (newarr != null) ret.add(newarr)
                    newarr = JSONArray()
                    count = 20
                }
                newarr?.put(array[i])
                --count
            }
            if (newarr != null && newarr.length() > 0) ret.add(newarr)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
            ret = ArrayList()
            ret.add(array)
        }
        return ret
    }

    private fun broadcast(intent: Intent) {
        context.packageManager.safeQueryBroadcastReceivers(intent, 0).forEach { resolveInfo ->
            resolveInfo.activityInfo.packageName?.let {
                intent.setPackage(it)
                context.sendBroadcast(intent)
                aapsLogger.debug(LTag.CORE, "Sending broadcast " + intent.action + " to: " + it)
            }
        }
    }
}