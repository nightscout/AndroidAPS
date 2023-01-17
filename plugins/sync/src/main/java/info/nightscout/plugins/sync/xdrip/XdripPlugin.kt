package info.nightscout.plugins.sync.xdrip

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.toStringShort
import info.nightscout.core.iob.generateCOBString
import info.nightscout.core.iob.round
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.plugins.sync.R
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAppInitialized
import info.nightscout.rx.events.EventAutosensCalculationFinished
import info.nightscout.rx.events.EventConfigBuilderChange
import info.nightscout.rx.events.EventExtendedBolusChange
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.rx.events.EventRefreshOverview
import info.nightscout.rx.events.EventTempBasalChange
import info.nightscout.rx.events.EventTreatmentChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
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
            .observeOn(aapsSchedulers.io)
            .subscribe({ if (lastLoopStatus != (loop as PluginBase).isEnabled()) sendStatus() }, fabricPrivacy::logException)
        disposable += rxBus.toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendStatus() }, fabricPrivacy::logException)
        disposable += rxBus.toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendStatus() }, fabricPrivacy::logException)
        disposable += rxBus.toObservable(EventTreatmentChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendStatus() }, fabricPrivacy::logException)
        disposable += rxBus.toObservable(EventConfigBuilderChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendStatus() }, fabricPrivacy::logException)
        disposable += rxBus.toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendStatus() }, fabricPrivacy::logException)
        disposable += rxBus.toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendStatus() }, fabricPrivacy::logException)
        disposable += rxBus.toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendStatus() }, fabricPrivacy::logException)
    }

    override fun onStop() {
        super.onStop()
        disposable.clear()
        sendStatus()
    }

    private fun sendStatus() {
        if (sp.getBoolean(R.string.key_xdrip_send_status, false)) {
            val status = profileFunction.getProfile()?.let { buildStatusLine(it) } ?: ""
            context.sendBroadcast(
                Intent(ACTION_NEW_EXTERNAL_STATUSLINE).also {
                    it.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    it.putExtras(Bundle().apply { putString(EXTRA_STATUSLINE, status) })
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
}