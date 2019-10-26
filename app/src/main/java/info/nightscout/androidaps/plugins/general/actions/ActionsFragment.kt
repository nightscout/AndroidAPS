package info.nightscout.androidaps.plugins.general.actions


import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.HistoryBrowseActivity
import info.nightscout.androidaps.activities.TDDStatsActivity
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction
import info.nightscout.androidaps.plugins.general.actions.dialogs.FillDialog
import info.nightscout.androidaps.plugins.general.actions.dialogs.NewExtendedBolusDialog
import info.nightscout.androidaps.plugins.general.actions.dialogs.NewTempBasalDialog
import info.nightscout.androidaps.plugins.general.careportal.CareportalFragment
import info.nightscout.androidaps.plugins.general.careportal.Dialogs.NewNSTreatmentDialog
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.actions_fragment.*
import java.util.*

class ActionsFragment : Fragment() {

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
            val newDialog = NewNSTreatmentDialog()
            val profileSwitch = CareportalFragment.PROFILESWITCH
            profileSwitch.executeProfileSwitch = true
            newDialog.setOptions(profileSwitch, R.string.careportal_profileswitch)
            fragmentManager?.let { newDialog.show(it, "NewNSTreatmentDialog") }
        }
        actions_temptarget.setOnClickListener {
            val newTTDialog = NewNSTreatmentDialog()
            val temptarget = CareportalFragment.TEMPTARGET
            temptarget.executeTempTarget = true
            newTTDialog.setOptions(temptarget, R.string.careportal_temporarytarget)
            fragmentManager?.let { newTTDialog.show(it, "NewNSTreatmentDialog") }
        }
        actions_extendedbolus.setOnClickListener {
            fragmentManager?.let { NewExtendedBolusDialog().show(it, "NewExtendedDialog") }
        }
        actions_extendedbolus_cancel.setOnClickListener {
            if (TreatmentsPlugin.getPlugin().isInHistoryExtendedBoluslInProgress) {
                ConfigBuilderPlugin.getPlugin().commandQueue.cancelExtended(object : Callback() {
                    override fun run() {
                        if (!result.success)
                            ToastUtils.showToastInUiThread(MainApp.instance().applicationContext, MainApp.gs(R.string.extendedbolusdeliveryerror))
                    }
                })
            }
        }
        actions_settempbasal.setOnClickListener { fragmentManager?.let { NewTempBasalDialog().show(it, "NewTempDialog") } }
        actions_canceltempbasal.setOnClickListener {
            if (TreatmentsPlugin.getPlugin().isTempBasalInProgress) {
                ConfigBuilderPlugin.getPlugin().commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success)
                            ToastUtils.showToastInUiThread(MainApp.instance().applicationContext, MainApp.gs(R.string.tempbasaldeliveryerror))
                    }
                })
            }
        }
        actions_fill.setOnClickListener { fragmentManager?.let { FillDialog().show(it, "FillDialog") } }
        actions_historybrowser.setOnClickListener { startActivity(Intent(context, HistoryBrowseActivity::class.java)) }
        actions_tddstats.setOnClickListener { startActivity(Intent(context, TDDStatsActivity::class.java)) }

        SP.putBoolean(R.string.key_objectiveuseactions, true)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += RxBus
                .toObservable(EventInitializationChanged::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    updateGui()
                }, {
                    FabricPrivacy.logException(it)
                })
        disposable += RxBus
                .toObservable(EventRefreshOverview::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    updateGui()
                }, {
                    FabricPrivacy.logException(it)
                })
        disposable += RxBus
                .toObservable(EventExtendedBolusChange::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    updateGui()
                }, {
                    FabricPrivacy.logException(it)
                })
        disposable += RxBus
                .toObservable(EventTempBasalChange::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    updateGui()
                }, {
                    FabricPrivacy.logException(it)
                })
        disposable += RxBus
                .toObservable(EventCustomActionsChanged::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    updateGui()
                }, {
                    FabricPrivacy.logException(it)
                })
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
                if (ConfigBuilderPlugin.getPlugin().activeProfileInterface?.profile != null) View.VISIBLE
                else View.GONE

        if (ProfileFunctions.getInstance().profile == null) {
            actions_temptarget?.visibility = View.GONE
            actions_extendedbolus?.visibility = View.GONE
            actions_extendedbolus_cancel?.visibility = View.GONE
            actions_settempbasal?.visibility = View.GONE
            actions_canceltempbasal?.visibility = View.GONE
            actions_fill?.visibility = View.GONE
            return
        }

        val pump = ConfigBuilderPlugin.getPlugin().activePump ?: return
        val basalProfileEnabled = MainApp.isEngineeringModeOrRelease() && pump.pumpDescription.isSetBasalProfileCapable

        actions_profileswitch?.visibility = if (!basalProfileEnabled || !pump.isInitialized || pump.isSuspended) View.GONE else View.VISIBLE

        if (!pump.pumpDescription.isExtendedBolusCapable || !pump.isInitialized || pump.isSuspended || pump.isFakingTempsByExtendedBoluses || Config.APS) {
            actions_extendedbolus?.visibility = View.GONE
            actions_extendedbolus_cancel?.visibility = View.GONE
        } else {
            val activeExtendedBolus = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(System.currentTimeMillis())
            if (activeExtendedBolus != null) {
                actions_extendedbolus?.visibility = View.GONE
                actions_extendedbolus_cancel?.visibility = View.VISIBLE
                actions_extendedbolus_cancel?.text = MainApp.gs(R.string.cancel) + " " + activeExtendedBolus.toString()
            } else {
                actions_extendedbolus?.visibility = View.VISIBLE
                actions_extendedbolus_cancel?.visibility = View.GONE
            }
        }

        if (!pump.pumpDescription.isTempBasalCapable || !pump.isInitialized || pump.isSuspended) {
            actions_settempbasal?.visibility = View.GONE
            actions_canceltempbasal?.visibility = View.GONE
        } else {
            val activeTemp = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis())
            if (activeTemp != null) {
                actions_settempbasal?.visibility = View.GONE
                actions_canceltempbasal?.visibility = View.VISIBLE
                actions_canceltempbasal?.text = MainApp.gs(R.string.cancel) + " " + activeTemp.toStringShort()
            } else {
                actions_settempbasal?.visibility = View.VISIBLE
                actions_canceltempbasal?.visibility = View.GONE
            }
        }

        actions_fill?.visibility =
                if (!pump.pumpDescription.isRefillingCapable || !pump.isInitialized || pump.isSuspended) View.GONE
                else View.VISIBLE

        actions_temptarget?.visibility = if (!Config.APS) View.GONE else View.VISIBLE
        actions_tddstats?.visibility = if (!pump.pumpDescription.supportsTDDs) View.GONE else View.VISIBLE
        checkPumpCustomActions()
    }

    private fun checkPumpCustomActions() {
        val activePump = ConfigBuilderPlugin.getPlugin().activePump ?: return
        val customActions = activePump.customActions ?: return
        removePumpCustomActions()

        for (customAction in customActions) {
            if (!customAction.isEnabled) continue

            val btn = SingleClickButton(context, null, android.R.attr.buttonStyle)
            btn.text = MainApp.gs(customAction.name)

            val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0.5f)
            layoutParams.setMargins(20, 8, 20, 8) // 10,3,10,3

            btn.layoutParams = layoutParams
            btn.setOnClickListener { v ->
                val b = v as SingleClickButton
                val action = this.pumpCustomActions[b.text.toString()]
                ConfigBuilderPlugin.getPlugin().activePump!!.executeCustomAction(action!!.customActionType)
            }

            val top = resources.getDrawable(customAction.iconResourceId)
            btn.setCompoundDrawablesWithIntrinsicBounds(null, top, null, null)

            action_buttons_layout?.addView(btn)

            this.pumpCustomActions[MainApp.gs(customAction.name)] = customAction
            this.pumpCustomButtons.add(btn)
        }
    }

    private fun removePumpCustomActions() {
        for (customButton in pumpCustomButtons) action_buttons_layout?.removeView(customButton)
        pumpCustomButtons.clear()
    }
}