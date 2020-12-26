package info.nightscout.androidaps.dialogs

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.FragmentManager
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.aps.loop.events.EventNewOpenLoopNotification
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.dialog_loop.*
import javax.inject.Inject

class LoopDialog : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var mainApp: MainApp
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin

    private var disposable: CompositeDisposable = CompositeDisposable()

    private var showOkCancel: Boolean = true

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("showOkCancel", if (showOkCancel) 1 else 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            showOkCancel = bundle.getInt("showOkCancel", 1) == 1
        }
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        return inflater.inflate(R.layout.dialog_loop, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        updateGUI("LoopDialogOnViewCreated")

        overview_disable?.setOnClickListener { if(showOkCancel) onClick_OkCancelEnabled(it) else onClick(it); dismiss() }
        overview_enable?.setOnClickListener { if(showOkCancel) onClick_OkCancelEnabled(it) else onClick(it); dismiss() }
        overview_resume?.setOnClickListener { if(showOkCancel) onClick_OkCancelEnabled(it) else onClick(it); dismiss() }
        overview_reconnect?.setOnClickListener { if(showOkCancel) onClick_OkCancelEnabled(it) else onClick(it); dismiss() }
        overview_suspend_1h?.setOnClickListener { if(showOkCancel) onClick_OkCancelEnabled(it) else onClick(it); dismiss() }
        overview_suspend_2h?.setOnClickListener { if(showOkCancel) onClick_OkCancelEnabled(it) else onClick(it); dismiss() }
        overview_suspend_3h?.setOnClickListener { if(showOkCancel) onClick_OkCancelEnabled(it) else onClick(it); dismiss() }
        overview_suspend_10h?.setOnClickListener { if(showOkCancel) onClick_OkCancelEnabled(it) else onClick(it); dismiss() }
        overview_disconnect_15m?.setOnClickListener { if(showOkCancel) onClick_OkCancelEnabled(it) else onClick(it); dismiss() }
        overview_disconnect_30m?.setOnClickListener { if(showOkCancel) onClick_OkCancelEnabled(it) else onClick(it); dismiss() }
        overview_disconnect_1h?.setOnClickListener { if(showOkCancel) onClick_OkCancelEnabled(it) else onClick(it); dismiss() }
        overview_disconnect_2h?.setOnClickListener { if(showOkCancel) onClick_OkCancelEnabled(it) else onClick(it); dismiss() }
        overview_disconnect_3h?.setOnClickListener { if(showOkCancel) onClick_OkCancelEnabled(it) else onClick(it); dismiss() }

        // cancel button
        cancel?.setOnClickListener { dismiss() }

        // bus
        disposable.add(rxBus
            .toObservable(EventNewOpenLoopNotification::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                activity?.runOnUiThread { updateGUI("EventNewOpenLoopNotification") }
            }, { fabricPrivacy.logException(it) })
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
    }

    fun updateGUI(from: String) {
        aapsLogger.debug("UpdateGUI from $from")
        val pumpDescription: PumpDescription = activePlugin.activePump.pumpDescription
        if (profileFunction.isProfileValid("LoopDialogUpdateGUI")) {
            if (loopPlugin.isEnabled(PluginType.LOOP)) {
                overview_enable?.visibility = View.GONE          //sp.getBoolean(R.string.key_usesuperbolus, false).toVisibility()
                overview_disable?.visibility = View.VISIBLE
                overview_loop_header?.text = resourceHelper.gs(R.string.disableloop)
                if (!loopPlugin.isSuspended) {
                    overview_suspend_header?.text=resourceHelper.gs(R.string.suspendloop)
                    overview_resume?.visibility = View.GONE
                    overview_suspend_buttons?.visibility=View.VISIBLE
                    overview_suspend?.visibility=View.VISIBLE
                } else {
                    if (!loopPlugin.isDisconnected) {
                        overview_suspend_header?.text = resourceHelper.gs(R.string.resumeloop)
                        overview_resume?.visibility = View.VISIBLE
                        overview_suspend_buttons?.visibility=View.GONE
                        overview_suspend?.visibility=View.VISIBLE
                    } else
                        overview_suspend?.visibility = View.GONE
                }
            } else {
                overview_enable?.visibility = View.VISIBLE
                overview_disable?.visibility = View.GONE
                overview_loop_header?.text = resourceHelper.gs(R.string.enableloop)
                overview_suspend?.visibility = View.GONE
            }
            if (!loopPlugin.isDisconnected) {
                overview_pump_header?.text = resourceHelper.gs(R.string.disconnectpump)
                overview_disconnect_15m?.visibility = if (pumpDescription.tempDurationStep15mAllowed) View.VISIBLE else View.GONE
                overview_disconnect_15m?.visibility = if (pumpDescription.tempDurationStep30mAllowed) View.VISIBLE else View.GONE
                overview_disconnect_buttons?.visibility = View.VISIBLE
                overview_reconnect?.visibility = View.GONE
            } else {
                overview_pump_header?.text = resourceHelper.gs(R.string.reconnect)
                overview_disconnect_buttons?.visibility = View.GONE
                overview_reconnect?.visibility = View.VISIBLE
            }
        }
        val profile = profileFunction.getProfile()
        val profileStore = activePlugin.activeProfileInterface.profile

        if (profile == null || profileStore == null) {
            ToastUtils.showToastInUiThread(mainApp, resourceHelper.gs(R.string.noprofile))
            dismiss()
            return
        }

    }

    fun onClick_OkCancelEnabled(v: View): Boolean {
        var description = ""
        when(v.id) {
            R.id.overview_disable           -> description = resourceHelper.gs(R.string.disableloop)
            R.id.overview_enable            -> description = resourceHelper.gs(R.string.enableloop)
            R.id.overview_resume            -> description = resourceHelper.gs(R.string.resume)
            R.id.overview_reconnect         -> description = resourceHelper.gs(R.string.reconnect)
            R.id.overview_suspend_1h        -> description = resourceHelper.gs(R.string.suspendloopfor1h)
            R.id.overview_suspend_2h        -> description = resourceHelper.gs(R.string.suspendloopfor2h)
            R.id.overview_suspend_3h        -> description = resourceHelper.gs(R.string.suspendloopfor3h)
            R.id.overview_suspend_10h       -> description = resourceHelper.gs(R.string.suspendloopfor10h)
            R.id.overview_disconnect_15m    -> description = resourceHelper.gs(R.string.disconnectpumpfor15m)
            R.id.overview_disconnect_30m    -> description = resourceHelper.gs(R.string.disconnectpumpfor30m)
            R.id.overview_disconnect_1h     -> description = resourceHelper.gs(R.string.disconnectpumpfor1h)
            R.id.overview_disconnect_2h     -> description = resourceHelper.gs(R.string.disconnectpumpfor2h)
            R.id.overview_disconnect_3h     -> description = resourceHelper.gs(R.string.disconnectpumpfor3h)
        }
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.confirm), description, Runnable {
                onClick(v)
            })
        }
        return true
    }

    fun onClick(v: View): Boolean {
        val profile = profileFunction.getProfile() ?: return true
        when (v.id) {
            R.id.overview_disable                               -> {
                aapsLogger.debug("USER ENTRY: LOOP DISABLED")
                loopPlugin.setPluginEnabled(PluginType.LOOP, false)
                loopPlugin.setFragmentVisible(PluginType.LOOP, false)
                configBuilderPlugin.storeSettings("DisablingLoop")
                rxBus.send(EventRefreshOverview("suspendmenu"))
                commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            ToastUtils.showToastInUiThread(context, resourceHelper.gs(R.string.tempbasaldeliveryerror))
                        }
                    }
                })
                loopPlugin.createOfflineEvent(24 * 60) // upload 24h, we don't know real duration
                return true
            }

            R.id.overview_enable                                -> {
                aapsLogger.debug("USER ENTRY: LOOP ENABLED")
                loopPlugin.setPluginEnabled(PluginType.LOOP, true)
                loopPlugin.setFragmentVisible(PluginType.LOOP, true)
                configBuilderPlugin.storeSettings("EnablingLoop")
                rxBus.send(EventRefreshOverview("suspendmenu"))
                loopPlugin.createOfflineEvent(0)
                return true
            }

            R.id.overview_resume, R.id.overview_reconnect       -> {
                aapsLogger.debug("USER ENTRY: RESUME")
                loopPlugin.suspendTo(0L)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            val i = Intent(context, ErrorHelperActivity::class.java)
                            i.putExtra("soundid", R.raw.boluserror)
                            i.putExtra("status", result.comment)
                            i.putExtra("title", resourceHelper.gs(R.string.tempbasaldeliveryerror))
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context?.startActivity(i)
                        }
                    }
                })
                sp.putBoolean(R.string.key_objectiveusereconnect, true)
                loopPlugin.createOfflineEvent(0)
                return true
            }

            R.id.overview_suspend_1h                            -> {
                aapsLogger.debug("USER ENTRY: SUSPEND 1h")
                loopPlugin.suspendLoop(60)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            R.id.overview_suspend_2h                            -> {
                aapsLogger.debug("USER ENTRY: SUSPEND 2h")
                loopPlugin.suspendLoop(120)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            R.id.overview_suspend_3h                            -> {
                aapsLogger.debug("USER ENTRY: SUSPEND 3h")
                loopPlugin.suspendLoop(180)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            R.id.overview_suspend_10h                           -> {
                aapsLogger.debug("USER ENTRY: SUSPEND 10h")
                loopPlugin.suspendLoop(600)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            R.id.overview_disconnect_15m                        -> {
                aapsLogger.debug("USER ENTRY: DISCONNECT 15m")
                loopPlugin.disconnectPump(15, profile)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            R.id.overview_disconnect_30m                        -> {
                aapsLogger.debug("USER ENTRY: DISCONNECT 30m")
                loopPlugin.disconnectPump(30, profile)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            R.id.overview_disconnect_1h                         -> {
                aapsLogger.debug("USER ENTRY: DISCONNECT 1h")
                loopPlugin.disconnectPump(60, profile)
                sp.putBoolean(R.string.key_objectiveusedisconnect, true)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            R.id.overview_disconnect_2h                         -> {
                aapsLogger.debug("USER ENTRY: DISCONNECT 2h")
                loopPlugin.disconnectPump(120, profile)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            R.id.overview_disconnect_3h                         -> {
                aapsLogger.debug("USER ENTRY: DISCONNECT 3h")
                loopPlugin.disconnectPump(180, profile)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }
        }
        return false
    }

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            manager.beginTransaction().let {
                it.add(this, tag)
                it.commitAllowingStateLoss()
            }
        } catch (e: IllegalStateException) {
            aapsLogger.debug(e.localizedMessage)
        }
    }
}
