package app.aaps.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.FragmentManager
import app.aaps.core.data.model.RM
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogLoopBinding
import dagger.android.support.DaggerDialogFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class LoopDialog : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var ctx: Context
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var loop: Loop
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var config: Config
    @Inject lateinit var translator: Translator

    private var queryingProtection = false
    private var showOkCancel: Boolean = true
    private var _binding: DialogLoopBinding? = null
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshDialog: Runnable

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    val disposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putBoolean("showOkCancel", showOkCancel)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            showOkCancel = bundle.getBoolean("showOkCancel", true)
        }
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        _binding = DialogLoopBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        updateGUI("LoopDialogOnViewCreated")

        binding.overviewCloseloop.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewLgsloop.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewOpenloop.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisable.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewResume.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewReconnect.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewSuspend1h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewSuspend2h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewSuspend3h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewSuspend10h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect15m.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect30m.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect1h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect2h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect3h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }

        // cancel button
        binding.cancel.setOnClickListener { dismiss() }

        refreshDialog = Runnable {
            runOnUiThread { updateGUI("refreshDialog") }
            handler.postDelayed(refreshDialog, 15 * 1000L)
        }
        handler.postDelayed(refreshDialog, 15 * 1000L)
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
        disposable.clear()
    }

    @Synchronized
    fun updateGUI(from: String) {
        if (_binding == null) return
        aapsLogger.debug("UpdateGUI from $from")
        val pumpDescription: PumpDescription = activePlugin.activePump.pumpDescription

        val runningModeRecord = loop.runningModeRecord
        val runningMode = loop.runningModeRecord.mode
        val allowedModes = loop.allowedNextModes()

        binding.runningMode.text = translator.translate(runningMode)
        if (runningModeRecord.reasons?.isNotEmpty() == true) {
            binding.overviewReasonsLayout.visibility = View.VISIBLE
            binding.overviewReasons.text = runningModeRecord.reasons
        } else binding.overviewReasonsLayout.visibility = View.GONE

        binding.overviewLoop.visibility = (
            allowedModes.contains(RM.Mode.DISABLED_LOOP) ||
                allowedModes.contains(RM.Mode.OPEN_LOOP) ||
                allowedModes.contains(RM.Mode.CLOSED_LOOP) ||
                allowedModes.contains(RM.Mode.CLOSED_LOOP_LGS)
            ).toVisibility()
        binding.overviewSuspend.visibility = (
            allowedModes.contains(RM.Mode.SUSPENDED_BY_USER) ||
                allowedModes.contains(RM.Mode.RESUME) && runningMode == RM.Mode.SUSPENDED_BY_USER
            ).toVisibility()
        binding.overviewPump.visibility = (
            allowedModes.contains(RM.Mode.DISCONNECTED_PUMP) ||
                allowedModes.contains(RM.Mode.RESUME) && runningMode == RM.Mode.DISCONNECTED_PUMP
            ).toVisibility()
        binding.overviewDisconnectButtons.visibility = (allowedModes.contains(RM.Mode.DISCONNECTED_PUMP) && config.APS).toVisibility()
        binding.overviewPumpHeader.visibility = config.APS.toVisibility()
        binding.overviewReconnect.visibility = (allowedModes.contains(RM.Mode.RESUME) && runningMode == RM.Mode.DISCONNECTED_PUMP).toVisibility()
        binding.overviewSuspendButtons.visibility = allowedModes.contains(RM.Mode.SUSPENDED_BY_USER).toVisibility()
        binding.overviewResume.visibility = (allowedModes.contains(RM.Mode.RESUME) && runningMode == RM.Mode.SUSPENDED_BY_USER).toVisibility()
        binding.overviewDisable.visibility = allowedModes.contains(RM.Mode.DISABLED_LOOP).toVisibility()
        binding.overviewCloseloop.visibility = allowedModes.contains(RM.Mode.CLOSED_LOOP).toVisibility()
        binding.overviewLgsloop.visibility = allowedModes.contains(RM.Mode.CLOSED_LOOP_LGS).toVisibility()
        binding.overviewOpenloop.visibility = allowedModes.contains(RM.Mode.OPEN_LOOP).toVisibility()

        binding.overviewDisconnect15m.visibility = pumpDescription.tempDurationStep15mAllowed.toVisibility()
        binding.overviewDisconnect30m.visibility = pumpDescription.tempDurationStep30mAllowed.toVisibility()

        if (runningMode == RM.Mode.SUSPENDED_BY_USER) binding.overviewSuspendHeader.text = rh.gs(app.aaps.core.ui.R.string.resumeloop)
        else binding.overviewSuspendHeader.text = rh.gs(app.aaps.core.ui.R.string.suspendloop)

        if (runningMode == RM.Mode.DISCONNECTED_PUMP) binding.overviewPumpHeader.text = rh.gs(R.string.reconnect)
        else binding.overviewPumpHeader.text = rh.gs(R.string.disconnectpump)

    }

    private fun onClickOkCancelEnabled(v: View): Boolean {
        var description = ""
        when (v.id) {
            R.id.overview_closeloop      -> description = rh.gs(app.aaps.core.ui.R.string.closedloop)
            R.id.overview_lgsloop        -> description = rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)
            R.id.overview_openloop       -> description = rh.gs(app.aaps.core.ui.R.string.openloop)
            R.id.overview_disable        -> description = rh.gs(app.aaps.core.ui.R.string.disableloop)
            R.id.overview_resume         -> description = rh.gs(R.string.resume)
            R.id.overview_reconnect      -> description = rh.gs(R.string.reconnect)
            R.id.overview_suspend_1h     -> description = rh.gs(R.string.suspendloopfor1h)
            R.id.overview_suspend_2h     -> description = rh.gs(R.string.suspendloopfor2h)
            R.id.overview_suspend_3h     -> description = rh.gs(R.string.suspendloopfor3h)
            R.id.overview_suspend_10h    -> description = rh.gs(R.string.suspendloopfor10h)
            R.id.overview_disconnect_15m -> description = rh.gs(R.string.disconnectpumpfor15m)
            R.id.overview_disconnect_30m -> description = rh.gs(R.string.disconnectpumpfor30m)
            R.id.overview_disconnect_1h  -> description = rh.gs(R.string.disconnectpumpfor1h)
            R.id.overview_disconnect_2h  -> description = rh.gs(R.string.disconnectpumpfor2h)
            R.id.overview_disconnect_3h  -> description = rh.gs(R.string.disconnectpumpfor3h)
        }
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.confirm), description, Runnable {
                onClick(v)
            })
        }
        return true
    }

    private fun onClick(v: View): Boolean {
        val profile = profileFunction.getProfile() ?: return false
        when (v.id) {
            R.id.overview_closeloop                       -> {
                loop.handleRunningModeChange(newRM = RM.Mode.CLOSED_LOOP, action = Action.CLOSED_LOOP_MODE, source = Sources.LoopDialog, profile = profile)
                return true
            }

            R.id.overview_lgsloop                         -> {
                loop.handleRunningModeChange(newRM = RM.Mode.CLOSED_LOOP_LGS, action = Action.LGS_LOOP_MODE, source = Sources.LoopDialog, profile = profile)
                return true
            }

            R.id.overview_openloop                        -> {
                loop.handleRunningModeChange(newRM = RM.Mode.OPEN_LOOP, action = Action.OPEN_LOOP_MODE, source = Sources.LoopDialog, profile = profile)
                return true
            }

            R.id.overview_disable                         -> {
                loop.handleRunningModeChange(newRM = RM.Mode.DISABLED_LOOP, durationInMinutes = Int.MAX_VALUE, action = Action.LOOP_DISABLED, source = Sources.LoopDialog, profile = profile)
                return true
            }

            R.id.overview_resume, R.id.overview_reconnect -> {
                loop.handleRunningModeChange(newRM = RM.Mode.RESUME, action = if (v.id == R.id.overview_resume) Action.RESUME else Action.RECONNECT, source = Sources.LoopDialog, profile = profile)
                preferences.put(BooleanNonKey.ObjectivesReconnectUsed, true)
                return true
            }

            R.id.overview_suspend_1h                      -> {
                loop.handleRunningModeChange(newRM = RM.Mode.SUSPENDED_BY_USER, durationInMinutes = T.hours(1).mins().toInt(), action = Action.SUSPEND, source = Sources.LoopDialog, profile = profile)
                return true
            }

            R.id.overview_suspend_2h                      -> {
                loop.handleRunningModeChange(newRM = RM.Mode.SUSPENDED_BY_USER, durationInMinutes = T.hours(2).mins().toInt(), action = Action.SUSPEND, source = Sources.LoopDialog, profile = profile)
                return true
            }

            R.id.overview_suspend_3h                      -> {
                loop.handleRunningModeChange(newRM = RM.Mode.SUSPENDED_BY_USER, durationInMinutes = T.hours(3).mins().toInt(), action = Action.SUSPEND, source = Sources.LoopDialog, profile = profile)
                return true
            }

            R.id.overview_suspend_10h                     -> {
                loop.handleRunningModeChange(newRM = RM.Mode.SUSPENDED_BY_USER, durationInMinutes = T.hours(10).mins().toInt(), action = Action.SUSPEND, source = Sources.LoopDialog, profile = profile)
                return true
            }

            R.id.overview_disconnect_15m                  -> {
                loop.handleRunningModeChange(newRM = RM.Mode.DISCONNECTED_PUMP, durationInMinutes = 15, action = Action.DISCONNECT, source = Sources.LoopDialog, profile = profile)
                return true
            }

            R.id.overview_disconnect_30m                  -> {
                loop.handleRunningModeChange(newRM = RM.Mode.DISCONNECTED_PUMP, durationInMinutes = 30, action = Action.DISCONNECT, source = Sources.LoopDialog, profile = profile)
                return true
            }

            R.id.overview_disconnect_1h                   -> {
                loop.handleRunningModeChange(newRM = RM.Mode.DISCONNECTED_PUMP, durationInMinutes = 60, action = Action.DISCONNECT, source = Sources.LoopDialog, profile = profile)
                preferences.put(BooleanNonKey.ObjectivesDisconnectUsed, true)
                return true
            }

            R.id.overview_disconnect_2h                   -> {
                loop.handleRunningModeChange(newRM = RM.Mode.DISCONNECTED_PUMP, durationInMinutes = 120, action = Action.DISCONNECT, source = Sources.LoopDialog, profile = profile)
                return true
            }

            R.id.overview_disconnect_3h                   -> {
                loop.handleRunningModeChange(newRM = RM.Mode.DISCONNECTED_PUMP, durationInMinutes = 180, action = Action.DISCONNECT, source = Sources.LoopDialog, profile = profile)
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
            aapsLogger.debug(e.localizedMessage ?: e.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        if (!queryingProtection) {
            queryingProtection = true
            activity?.let { activity ->
                val cancelFail = {
                    queryingProtection = false
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                    ToastUtils.warnToast(ctx, R.string.dialog_canceled)
                    dismiss()
                }
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
    }
}