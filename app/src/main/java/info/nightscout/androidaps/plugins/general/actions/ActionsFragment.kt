package info.nightscout.androidaps.plugins.general.actions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.activities.TDDStatsActivity
import info.nightscout.androidaps.dialogs.*
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.historyBrowser.HistoryBrowseActivity
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction
import info.nightscout.androidaps.plugins.general.overview.StatusLightHandler
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.skins.SkinProvider
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.ui.SingleClickButton
import info.nightscout.androidaps.utils.ui.UIRunnable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.actions_fragment.*
import kotlinx.android.synthetic.main.careportal_stats_fragment.*
import java.util.*
import javax.inject.Inject

class ActionsFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var ctx: Context
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var statusLightHandler: StatusLightHandler
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var skinProvider: SkinProvider
    @Inject lateinit var config: Config

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val pumpCustomActions = HashMap<String, CustomAction>()
    private val pumpCustomButtons = ArrayList<SingleClickButton>()
    private var smallWidth = false
    private var smallHeight = false
    private lateinit var dm: DisplayMetrics

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        //check screen width
        dm = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(dm)

        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        smallWidth = screenWidth <= Constants.SMALL_WIDTH
        smallHeight = screenHeight <= Constants.SMALL_HEIGHT
        val landscape = screenHeight < screenWidth

        return inflater.inflate(skinProvider.activeSkin().actionsLayout(landscape, smallWidth), container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        actions_profileswitch.setOnClickListener {
            ProfileSwitchDialog().show(childFragmentManager, "Actions")
        }
        actions_temptarget.setOnClickListener {
            TempTargetDialog().show(childFragmentManager, "Actions")
        }
        actions_extendedbolus.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                    OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.extended_bolus), resourceHelper.gs(R.string.ebstopsloop),
                        Runnable {
                            ExtendedBolusDialog().show(childFragmentManager, "Actions")
                        }, null)
                })
            }
        }
        actions_extendedbolus_cancel.setOnClickListener {
            if (activePlugin.activeTreatments.isInHistoryExtendedBoluslInProgress) {
                aapsLogger.debug("USER ENTRY: CANCEL EXTENDED BOLUS")
                commandQueue.cancelExtended(object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            val i = Intent(ctx, ErrorHelperActivity::class.java)
                            i.putExtra("soundid", R.raw.boluserror)
                            i.putExtra("status", result.comment)
                            i.putExtra("title", resourceHelper.gs(R.string.extendedbolusdeliveryerror))
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(i)
                        }
                    }
                })
            }
        }
        actions_settempbasal.setOnClickListener {
            TempBasalDialog().show(childFragmentManager, "Actions")
        }
        actions_canceltempbasal.setOnClickListener {
            if (activePlugin.activeTreatments.isTempBasalInProgress) {
                aapsLogger.debug("USER ENTRY: CANCEL TEMP BASAL")
                commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            val i = Intent(ctx, ErrorHelperActivity::class.java)
                            i.putExtra("soundid", R.raw.boluserror)
                            i.putExtra("status", result.comment)
                            i.putExtra("title", resourceHelper.gs(R.string.tempbasaldeliveryerror))
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(i)
                        }
                    }
                })
            }
        }
        actions_fill.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable { FillDialog().show(childFragmentManager, "FillDialog") })
            }
        }
        actions_historybrowser.setOnClickListener { startActivity(Intent(context, HistoryBrowseActivity::class.java)) }
        actions_tddstats.setOnClickListener { startActivity(Intent(context, TDDStatsActivity::class.java)) }
        actions_bgcheck.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.BGCHECK, R.string.careportal_bgcheck).show(childFragmentManager, "Actions")
        }
        actions_cgmsensorinsert.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.SENSOR_INSERT, R.string.careportal_cgmsensorinsert).show(childFragmentManager, "Actions")
        }
        actions_pumpbatterychange.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.BATTERY_CHANGE, R.string.careportal_pumpbatterychange).show(childFragmentManager, "Actions")
        }
        actions_note.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.NOTE, R.string.careportal_note).show(childFragmentManager, "Actions")
        }
        actions_exercise.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.EXERCISE, R.string.careportal_exercise).show(childFragmentManager, "Actions")
        }
        actions_question.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.QUESTION, R.string.careportal_question).show(childFragmentManager, "Actions")
        }
        actions_announcement.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.ANNOUNCEMENT, R.string.careportal_announcement).show(childFragmentManager, "Actions")
        }

        sp.putBoolean(R.string.key_objectiveuseactions, true)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventCustomActionsChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventCareportalEventChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, { fabricPrivacy.logException(it) })
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    fun updateGui() {

        val profile = profileFunction.getProfile()
        val pump = activePlugin.activePump

        actions_profileswitch?.visibility = (
            activePlugin.activeProfileInterface.profile != null &&
                pump.pumpDescription.isSetBasalProfileCapable &&
                pump.isInitialized &&
                !pump.isSuspended).toVisibility()

        if (!pump.pumpDescription.isExtendedBolusCapable || !pump.isInitialized || pump.isSuspended || pump.isFakingTempsByExtendedBoluses) {
            actions_extendedbolus?.visibility = View.GONE
            actions_extendedbolus_cancel?.visibility = View.GONE
        } else {
            val activeExtendedBolus = activePlugin.activeTreatments.getExtendedBolusFromHistory(System.currentTimeMillis())
            if (activeExtendedBolus != null) {
                actions_extendedbolus?.visibility = View.GONE
                actions_extendedbolus_cancel?.visibility = View.VISIBLE
                @Suppress("SetTextI18n")
                actions_extendedbolus_cancel?.text = resourceHelper.gs(R.string.cancel) + " " + activeExtendedBolus.toStringMedium()
            } else {
                actions_extendedbolus?.visibility = View.VISIBLE
                actions_extendedbolus_cancel?.visibility = View.GONE
            }
        }

        if (!pump.pumpDescription.isTempBasalCapable || !pump.isInitialized || pump.isSuspended) {
            actions_settempbasal?.visibility = View.GONE
            actions_canceltempbasal?.visibility = View.GONE
        } else {
            val activeTemp = activePlugin.activeTreatments.getTempBasalFromHistory(System.currentTimeMillis())
            if (activeTemp != null) {
                actions_settempbasal?.visibility = View.GONE
                actions_canceltempbasal?.visibility = View.VISIBLE
                @Suppress("SetTextI18n")
                actions_canceltempbasal?.text = resourceHelper.gs(R.string.cancel) + " " + activeTemp.toStringShort()
            } else {
                actions_settempbasal?.visibility = View.VISIBLE
                actions_canceltempbasal?.visibility = View.GONE
            }
        }
        val activeBgSource = activePlugin.activeBgSource
        actions_historybrowser.visibility = (profile != null).toVisibility()
        actions_fill?.visibility = (pump.pumpDescription.isRefillingCapable && pump.isInitialized && !pump.isSuspended).toVisibility()
        actions_pumpbatterychange?.visibility = (pump.pumpDescription.isBatteryReplaceable || (pump is OmnipodPumpPlugin && pump.isUseRileyLinkBatteryLevel && pump.isBatteryChangeLoggingEnabled)).toVisibility()
        actions_temptarget?.visibility = (profile != null && config.APS).toVisibility()
        actions_tddstats?.visibility = pump.pumpDescription.supportsTDDs.toVisibility()

        if (!config.NSCLIENT) {
            statusLightHandler.updateStatusLights(careportal_canulaage, careportal_insulinage, careportal_reservoirlevel, careportal_sensorage, careportal_sensorlevel, careportal_pbage, careportal_batterylevel)
            careportal_senslevellabel?.text = if (activeBgSource.sensorBatteryLevel == -1) "" else resourceHelper.gs(R.string.careportal_level_label)
        } else {
            statusLightHandler.updateStatusLights(careportal_canulaage, careportal_insulinage, null, careportal_sensorage, null, careportal_pbage, null)
            careportal_senslevellabel?.text = ""
            careportal_inslevellabel?.text = ""
            careportal_pblevellabel?.text = ""
        }
        checkPumpCustomActions()

    }

    private fun checkPumpCustomActions() {
        val activePump = activePlugin.activePump
        val customActions = activePump.customActions ?: return
        val currentContext = context ?: return
        removePumpCustomActions()

        for (customAction in customActions) {
            if (!customAction.isEnabled) continue

            val btn = SingleClickButton(currentContext, null, android.R.attr.buttonStyle)
            btn.text = resourceHelper.gs(customAction.name)

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0.5f)
            layoutParams.setMargins(20, 8, 20, 8) // 10,3,10,3

            btn.layoutParams = layoutParams
            btn.setOnClickListener { v ->
                val b = v as SingleClickButton
                this.pumpCustomActions[b.text.toString()]?.let {
                    activePlugin.activePump.executeCustomAction(it.customActionType)
                }
            }
            val top = activity?.let { ContextCompat.getDrawable(it, customAction.iconResourceId) }
            btn.setCompoundDrawablesWithIntrinsicBounds(null, top, null, null)

            action_buttons_layout?.addView(btn)

            this.pumpCustomActions[resourceHelper.gs(customAction.name)] = customAction
            this.pumpCustomButtons.add(btn)
        }
    }

    private fun removePumpCustomActions() {
        for (customButton in pumpCustomButtons) action_buttons_layout?.removeView(customButton)
        pumpCustomButtons.clear()
    }
}