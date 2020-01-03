package info.nightscout.androidaps.plugins.general.actions

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.activities.TDDStatsActivity
import info.nightscout.androidaps.dialogs.*
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.historyBrowser.HistoryBrowseActivity
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction
import info.nightscout.androidaps.plugins.general.overview.StatusLightHandler
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.SingleClickButton
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.toVisibility
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.actions_fragment.*
import kotlinx.android.synthetic.main.careportal_stats_fragment.*
import java.util.*
import javax.inject.Inject

class ActionsFragment : DaggerFragment() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var mainApp: MainApp
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var statusLightHandler: StatusLightHandler
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val pumpCustomActions = HashMap<String, CustomAction>()
    private val pumpCustomButtons = ArrayList<SingleClickButton>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.actions_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        actions_profileswitch.setOnClickListener {
            fragmentManager?.let { ProfileSwitchDialog().show(it, "Actions") }
        }
        actions_temptarget.setOnClickListener {
            fragmentManager?.let { TempTargetDialog().show(it, "Actions") }
        }
        actions_extendedbolus.setOnClickListener {
            fragmentManager?.let { ExtendedBolusDialog().show(it, "Actions") }
        }
        actions_extendedbolus_cancel.setOnClickListener {
            if (treatmentsPlugin.isInHistoryExtendedBoluslInProgress) {
                configBuilderPlugin.commandQueue.cancelExtended(object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            val i = Intent(mainApp, ErrorHelperActivity::class.java)
                            i.putExtra("soundid", R.raw.boluserror)
                            i.putExtra("status", result.comment)
                            i.putExtra("title", resourceHelper.gs(R.string.extendedbolusdeliveryerror))
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            mainApp.startActivity(i)
                        }
                    }
                })
            }
        }
        actions_settempbasal.setOnClickListener {
            fragmentManager?.let { TempBasalDialog().show(it, "Actions") }
        }
        actions_canceltempbasal.setOnClickListener {
            if (treatmentsPlugin.isTempBasalInProgress) {
                configBuilderPlugin.commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            val i = Intent(mainApp, ErrorHelperActivity::class.java)
                            i.putExtra("soundid", R.raw.boluserror)
                            i.putExtra("status", result.comment)
                            i.putExtra("title", resourceHelper.gs(R.string.tempbasaldeliveryerror))
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            mainApp.startActivity(i)
                        }
                    }
                })
            }
        }
        actions_fill.setOnClickListener { fragmentManager?.let { FillDialog().show(it, "FillDialog") } }
        actions_historybrowser.setOnClickListener { startActivity(Intent(context, HistoryBrowseActivity::class.java)) }
        actions_tddstats.setOnClickListener { startActivity(Intent(context, TDDStatsActivity::class.java)) }
        actions_bgcheck.setOnClickListener {
            fragmentManager?.let { CareDialog().setOptions(CareDialog.EventType.BGCHECK, R.string.careportal_bgcheck).show(it, "Actions") }
        }
        actions_cgmsensorinsert.setOnClickListener {
            fragmentManager?.let { CareDialog().setOptions(CareDialog.EventType.SENSOR_INSERT, R.string.careportal_cgmsensorinsert).show(it, "Actions") }
        }
        actions_pumpbatterychange.setOnClickListener {
            fragmentManager?.let { CareDialog().setOptions(CareDialog.EventType.BATTERY_CHANGE, R.string.careportal_pumpbatterychange).show(it, "Actions") }
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
        actions_profileswitch?.visibility =
            if (configBuilderPlugin.activeProfileInterface.profile != null) View.VISIBLE
            else View.GONE

        if (profileFunction.getProfile() == null) {
            actions_temptarget?.visibility = View.GONE
            actions_extendedbolus?.visibility = View.GONE
            actions_extendedbolus_cancel?.visibility = View.GONE
            actions_settempbasal?.visibility = View.GONE
            actions_canceltempbasal?.visibility = View.GONE
            actions_fill?.visibility = View.GONE
            return
        }

        val pump = configBuilderPlugin.activePump ?: return
        val basalProfileEnabled = MainApp.isEngineeringModeOrRelease() && pump.pumpDescription.isSetBasalProfileCapable

        actions_profileswitch?.visibility = if (!basalProfileEnabled || !pump.isInitialized || pump.isSuspended) View.GONE else View.VISIBLE

        if (!pump.pumpDescription.isExtendedBolusCapable || !pump.isInitialized || pump.isSuspended || pump.isFakingTempsByExtendedBoluses || Config.APS) {
            actions_extendedbolus?.visibility = View.GONE
            actions_extendedbolus_cancel?.visibility = View.GONE
        } else {
            val activeExtendedBolus = treatmentsPlugin.getExtendedBolusFromHistory(System.currentTimeMillis())
            if (activeExtendedBolus != null) {
                actions_extendedbolus?.visibility = View.GONE
                actions_extendedbolus_cancel?.visibility = View.VISIBLE
                @Suppress("SetTextI18n")
                actions_extendedbolus_cancel?.text = resourceHelper.gs(R.string.cancel) + " " + activeExtendedBolus.toString()
            } else {
                actions_extendedbolus?.visibility = View.VISIBLE
                actions_extendedbolus_cancel?.visibility = View.GONE
            }
        }

        if (!pump.pumpDescription.isTempBasalCapable || !pump.isInitialized || pump.isSuspended) {
            actions_settempbasal?.visibility = View.GONE
            actions_canceltempbasal?.visibility = View.GONE
        } else {
            val activeTemp = treatmentsPlugin.getTempBasalFromHistory(System.currentTimeMillis())
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

        actions_fill?.visibility =
            if (!pump.pumpDescription.isRefillingCapable || !pump.isInitialized || pump.isSuspended) View.GONE
            else View.VISIBLE

        actions_temptarget?.visibility = Config.APS.toVisibility()
        actions_tddstats?.visibility = pump.pumpDescription.supportsTDDs.toVisibility()
        statusLightHandler.updateAge(careportal_sensorage, careportal_insulinage, careportal_canulaage, careportal_pbage)
        checkPumpCustomActions()
    }

    private fun checkPumpCustomActions() {
        val activePump = configBuilderPlugin.activePump ?: return
        val customActions = activePump.customActions ?: return
        removePumpCustomActions()

        for (customAction in customActions) {
            if (!customAction.isEnabled) continue

            val btn = SingleClickButton(context, null, android.R.attr.buttonStyle)
            btn.text = resourceHelper.gs(customAction.name)

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0.5f)
            layoutParams.setMargins(20, 8, 20, 8) // 10,3,10,3

            btn.layoutParams = layoutParams
            btn.setOnClickListener { v ->
                val b = v as SingleClickButton
                val action = this.pumpCustomActions[b.text.toString()]
                configBuilderPlugin.activePump!!.executeCustomAction(action!!.customActionType)
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