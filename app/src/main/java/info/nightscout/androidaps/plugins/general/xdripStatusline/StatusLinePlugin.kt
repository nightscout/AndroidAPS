package info.nightscout.androidaps.plugins.general.xdripStatusline

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusLinePlugin @Inject constructor(
    injector: HasAndroidInjector,
    private val sp: SP,
    private val profileFunction: ProfileFunction,
    resourceHelper: ResourceHelper,
    private val context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val activePlugin: ActivePluginProvider,
    private val loopPlugin: LoopPlugin,
    private val iobCobCalculatorPlugin: IobCobCalculatorPlugin,
    private val rxBus: RxBusWrapper,
    aapsLogger: AAPSLogger
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .pluginIcon((R.drawable.ic_blooddrop_48))
        .pluginName(R.string.xdripstatus)
        .shortName(R.string.xdripstatus_shortname)
        .neverVisible(true)
        .preferencesId(R.xml.pref_xdripstatus)
        .description(R.string.description_xdrip_status_line),
    aapsLogger, resourceHelper, injector
) {

    private val disposable = CompositeDisposable()
    private var lastLoopStatus = false

    companion object {
        //broadcast related constants
        @Suppress("SpellCheckingInspection")
        private const val EXTRA_STATUSLINE = "com.eveningoutpost.dexdrip.Extras.Statusline"

        @Suppress("SpellCheckingInspection")
        private const val ACTION_NEW_EXTERNAL_STATUSLINE = "com.eveningoutpost.dexdrip.ExternalStatusline"

        @Suppress("SpellCheckingInspection", "unused")
        private const val RECEIVER_PERMISSION = "com.eveningoutpost.dexdrip.permissions.RECEIVE_EXTERNAL_STATUSLINE"
    }

    override fun onStart() {
        super.onStart()
        disposable += rxBus.toObservable(EventRefreshOverview::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ if (lastLoopStatus != loopPlugin.isEnabled(PluginType.LOOP)) sendStatus() }) { fabricPrivacy.logException(it) }
        disposable += rxBus.toObservable(EventExtendedBolusChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendStatus() }) { fabricPrivacy.logException(it) }
        disposable += rxBus.toObservable(EventTempBasalChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendStatus() }) { fabricPrivacy.logException(it) }
        disposable += rxBus.toObservable(EventTreatmentChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendStatus() }) { fabricPrivacy.logException(it) }
        disposable += rxBus.toObservable(EventConfigBuilderChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendStatus() }) { fabricPrivacy.logException(it) }
        disposable += rxBus.toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendStatus() }) { fabricPrivacy.logException(it) }
        disposable += rxBus.toObservable(EventPreferenceChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendStatus() }) { fabricPrivacy.logException(it) }
        disposable += rxBus.toObservable(EventAppInitialized::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendStatus() }) { fabricPrivacy.logException(it) }
    }

    override fun onStop() {
        super.onStop()
        disposable.clear()
        sendStatus()
    }

    private fun sendStatus() {
        var status = "" // sent once on disable
        val profile = profileFunction.getProfile()
        if (isEnabled(PluginType.GENERAL) && profile != null) {
            status = buildStatusString(profile)
        }
        //sendData
        val bundle = Bundle()
        bundle.putString(EXTRA_STATUSLINE, status)
        val intent = Intent(ACTION_NEW_EXTERNAL_STATUSLINE)
        intent.putExtras(bundle)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        context.sendBroadcast(intent, null)
    }

    private fun buildStatusString(profile: Profile): String {
        var status = ""
        if (!loopPlugin.isEnabled(PluginType.LOOP)) {
            status += resourceHelper.gs(R.string.disabledloop) + "\n"
            lastLoopStatus = false
        } else if (loopPlugin.isEnabled(PluginType.LOOP)) {
            lastLoopStatus = true
        }
        //Temp basal
        val activeTemp = activePlugin.activeTreatments.getTempBasalFromHistory(System.currentTimeMillis())
        if (activeTemp != null) {
            status += activeTemp.toStringShort() + " "
        }
        //IOB
        activePlugin.activeTreatments.updateTotalIOBTreatments()
        val bolusIob = activePlugin.activeTreatments.lastCalculationTreatments.round()
        activePlugin.activeTreatments.updateTotalIOBTempBasals()
        val basalIob = activePlugin.activeTreatments.lastCalculationTempBasals.round()
        status += DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U"
        if (sp.getBoolean(R.string.key_xdripstatus_detailediob, true)) {
            status += ("("
                + DecimalFormatter.to2Decimal(bolusIob.iob) + "|"
                + DecimalFormatter.to2Decimal(basalIob.basaliob) + ")")
        }
        if (sp.getBoolean(R.string.key_xdripstatus_showbgi, true)) {
            val bgi = -(bolusIob.activity + basalIob.activity) * 5 * Profile.fromMgdlToUnits(profile.isfMgdl, profileFunction.getUnits())
            status += " " + (if (bgi >= 0) "+" else "") + DecimalFormatter.to2Decimal(bgi)
        }
        // COB
        status += " " + iobCobCalculatorPlugin.getCobInfo(false, "StatusLinePlugin").generateCOBString()
        return status
    }
}