package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.code.EventType
import info.nightscout.androidaps.plugins.pump.eopatch.code.PatchLifecycle
import info.nightscout.androidaps.plugins.pump.eopatch.code.PatchStep
import info.nightscout.androidaps.plugins.pump.eopatch.databinding.ActivityEopatchBinding
import info.nightscout.androidaps.plugins.pump.eopatch.extension.replaceFragmentInActivity
import info.nightscout.androidaps.plugins.pump.eopatch.extension.takeOne
import info.nightscout.androidaps.plugins.pump.eopatch.ui.dialogs.ProgressDialogHelper
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel
import info.nightscout.core.utils.extensions.safeGetSerializableExtra

class EopatchActivity : EoBaseActivity<ActivityEopatchBinding>() {
    private var mPatchCommCheckDialog: Dialog? = null
    private var mProgressDialog: AlertDialog? = null

    override fun getLayoutId(): Int = R.layout.activity_eopatch

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            binding.viewModel?.updateIncompletePatchActivationReminder()
        }

        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        title = getString(R.string.string_activate_patch)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.apply {
            viewModel = ViewModelProvider(this@EopatchActivity, viewModelFactory)[EopatchViewModel::class.java]
            viewModel?.apply {
                processIntent(intent)

                patchStep.observe(this@EopatchActivity) {
                    when (it) {
                        PatchStep.SAFE_DEACTIVATION          -> {
                            if (isActivated.value == true) {
                                setupViewFragment(EopatchSafeDeactivationFragment.newInstance())
                            } else {
                                this@EopatchActivity.finish()
                            }
                        }

                        PatchStep.MANUALLY_TURNING_OFF_ALARM -> setupViewFragment(EopatchTurningOffAlarmFragment.newInstance())
                        PatchStep.DISCARDED_FOR_CHANGE,
                        PatchStep.DISCARDED_FROM_ALARM,
                        PatchStep.DISCARDED                  -> setupViewFragment(EopatchRemoveFragment.newInstance())
                        PatchStep.WAKE_UP                    -> setupViewFragment(EopatchWakeUpFragment.newInstance())
                        PatchStep.CONNECT_NEW                -> setupViewFragment(EopatchConnectNewFragment.newInstance())
                        PatchStep.REMOVE_NEEDLE_CAP          -> setupViewFragment(EopatchRemoveNeedleCapFragment.newInstance())
                        PatchStep.REMOVE_PROTECTION_TAPE     -> setupViewFragment(EopatchRemoveProtectionTapeFragment.newInstance())
                        PatchStep.SAFETY_CHECK               -> setupViewFragment(EopatchSafetyCheckFragment.newInstance())
                        PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR,
                        PatchStep.ROTATE_KNOB                -> setupViewFragment(EopatchRotateKnobFragment.newInstance())
                        PatchStep.BASAL_SCHEDULE             -> setupViewFragment(EopatchBasalScheduleFragment.newInstance())
                        // PatchStep.SETTING_REMINDER_TIME -> setupViewFragment(PatchExpirationReminderSettingFragment.newInstance())
                        PatchStep.CHECK_CONNECTION           -> {
                            checkCommunication(
                                {
                                   setResult(RESULT_OK)
                                   this@EopatchActivity.finish()
                                }, {
                                   setResult(RESULT_CANCELED)
                                   this@EopatchActivity.finish()
                                }, {
                                   setResult(RESULT_DISCARDED)

                                   if (intent.getBooleanExtra(EXTRA_GO_HOME, true)) {
                                       backToHome()
                                   } else {
                                       this@EopatchActivity.finish()
                                   }
                                })
                        }

                        PatchStep.COMPLETE                   -> backToHome()

                        PatchStep.FINISH                     -> {
                            if (!intent.getBooleanExtra(EXTRA_START_FROM_MENU, false)
                                || intent.getBooleanExtra(EXTRA_GO_HOME, true)
                            ) {
                                backToHome()
                            } else {
                                this@EopatchActivity.finish()
                            }
                        }

                        PatchStep.BACK_TO_HOME               -> backToHome()
                        PatchStep.CANCEL                     -> this@EopatchActivity.finish()
                        else                                 -> Unit
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                binding.viewModel?.apply {
                    when (patchStep.value) {
                        PatchStep.WAKE_UP,
                        PatchStep.SAFE_DEACTIVATION -> this@EopatchActivity.finish()
                        else                        -> Unit
                    }
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {
        binding.viewModel?.apply {

            intent?.run {
                val step = intent.safeGetSerializableExtra(EXTRA_START_PATCH_STEP, PatchStep::class.java)

                forceDiscard = intent.getBooleanExtra(EXTRA_FORCE_DISCARD, false)
                isInAlarmHandling = intent.getBooleanExtra(EXTRA_IS_ALARM_HANDLING, false)
                if (intent.getBooleanExtra(EXTRA_START_WITH_COMM_CHECK, false)) {
                    checkCommunication({
                        initializePatchStep(step)
                    }, {
                        setResult(RESULT_CANCELED)
                        this@EopatchActivity.finish()
                    }, {
                        setResult(RESULT_DISCARDED)
                        this@EopatchActivity.finish()
                    }, doPreCheck = true)
                } else {
                    initializePatchStep(step)
                }
            }

            eventHandler.observe(this@EopatchActivity) { evt ->
                when (evt.peekContent()) {
                    EventType.SHOW_PATCH_COMM_DIALOG       -> {
                        if (mProgressDialog == null) {
                            mProgressDialog = ProgressDialogHelper.get(this@EopatchActivity, getString(evt.value as Int)).apply {
                                setCancelable(false)
                            }
                            mProgressDialog?.show()
                        }
                    }

                    EventType.DISMISS_PATCH_COMM_DIALOG    -> {
                        dismissProgressDialog()
                        // dismissRetryDialog()
                    }

                    EventType.SHOW_PATCH_COMM_ERROR_DIALOG -> {
                        dismissRetryDialog()
                        if (patchStep.value?.isSafeDeactivation == true || connectionTryCnt >= 2) {
                            val cancelLabel = commCheckCancelLabel.value ?: getString(R.string.cancel)
                            val message = "${getString(R.string.patch_comm_error_during_discard_desc_2)}\n${getString(R.string.patch_communication_check_helper_2)}"
                            mPatchCommCheckDialog = info.nightscout.core.ui.dialogs.AlertDialogHelper.Builder(this@EopatchActivity)
                                .setTitle(R.string.patch_communication_failed)
                                .setMessage(message)
                                .setCancelable(false)
                                .setPositiveButton(R.string.discard) { _, _ ->
                                    discardPatch()
                                }
                                .setNegativeButton(cancelLabel) { _, _ ->
                                    cancelPatchCommCheck()
                                }
                                .show()
                        } else {
                            val cancelLabel = commCheckCancelLabel.value ?: getString(R.string.cancel)
                            val message = "${getString(R.string.patch_communication_check_helper_1)}\n${getString(R.string.patch_communication_check_helper_2)}"
                            mPatchCommCheckDialog = info.nightscout.core.ui.dialogs.AlertDialogHelper.Builder(this@EopatchActivity)
                                .setTitle(R.string.patch_communication_failed)
                                .setMessage(message)
                                .setCancelable(false)
                                .setPositiveButton(R.string.retry) { _, _ ->
                                    retryCheckCommunication()
                                }
                                .setNegativeButton(cancelLabel) { _, _ ->
                                    cancelPatchCommCheck()
                                }
                                .show()
                        }
                    }

                    EventType.SHOW_BONDED_DIALOG           -> {
                        dismissProgressDialog()
                        info.nightscout.core.ui.dialogs.AlertDialogHelper.Builder(this@EopatchActivity)
                            .setTitle(R.string.patch_communication_succeed)
                            .setMessage(R.string.patch_communication_succeed_message)
                            .setPositiveButton(R.string.confirm) { _, _ ->
                                dismissPatchCommCheckDialogInternal(true)
                            }.show()
                    }

                    EventType.SHOW_CHANGE_PATCH_DIALOG     -> {
                        info.nightscout.core.ui.dialogs.AlertDialogHelper.Builder(this@EopatchActivity).apply {
                            setTitle(R.string.string_discard_patch)
                            setMessage(
                                when {
                                    patchState.isBolusActive && patchState.isTempBasalActive -> {
                                        R.string.patch_change_confirm_bolus_and_temp_basal_are_active_desc
                                    }

                                    patchState.isBolusActive                                 -> R.string.patch_change_confirm_bolus_is_active_desc
                                    patchState.isTempBasalActive                             -> R.string.patch_change_confirm_temp_basal_is_active_desc
                                    else                                                     -> R.string.patch_change_confirm_desc
                                }
                            )
                            setPositiveButton(R.string.string_discard_patch) { _, _ ->
                                deactivatePatch()
                            }
                            setNegativeButton(R.string.cancel) { _, _ ->

                            }
                        }.show()
                    }
                    // EventType.SHOW_BONDED_DIALOG           -> this@EopatchActivity.finish()
                    EventType.SHOW_DISCARD_DIALOG          -> {
                        val cancelLabel = isInAlarmHandling.takeOne(null, getString(R.string.cancel))
                        info.nightscout.core.ui.dialogs.AlertDialogHelper.Builder(this@EopatchActivity).apply {
                            setTitle(R.string.string_discard_patch)
                            if (isBolusActive) {
                                setMessage(R.string.patch_change_confirm_bolus_is_active_desc)
                            } else {
                                setMessage(R.string.string_are_you_sure_to_discard_current_patch)
                            }
                            setPositiveButton(R.string.discard) { _, _ ->
                                deactivate(true) {
                                    dismissPatchCommCheckDialogInternal()

                                    try {
                                        moveStep(isConnected.takeOne(PatchStep.DISCARDED, PatchStep.MANUALLY_TURNING_OFF_ALARM))
                                    } catch (e: IllegalStateException) {
                                        this@EopatchActivity.finish()
                                    }
                                }
                            }
                            setNegativeButton(cancelLabel) { _, _ ->
                                dismissProgressDialog()
                                updateIncompletePatchActivationReminder()
                            }
                        }.show()

                    }

                    else                                   -> Unit
                }
            }
        }
    }

    private fun dismissProgressDialog(){
        mProgressDialog?.let {
            try {
                mProgressDialog?.dismiss()
            } catch (ignored: IllegalStateException) {
            }
            mProgressDialog = null
        }
    }

    private fun dismissRetryDialog(){
        mPatchCommCheckDialog?.let {
            try {
                mPatchCommCheckDialog?.dismiss()
            } catch (ignored: IllegalStateException) {
            }
            mPatchCommCheckDialog = null
        }
    }

    private fun backToHome() {
        this@EopatchActivity.finish()
    }

    companion object {
        const val RESULT_DISCARDED = RESULT_FIRST_USER + 1
        const val EXTRA_START_PATCH_STEP = "EXTRA_START_PATCH_FRAGMENT_UI"
        const val EXTRA_START_FROM_MENU = "EXTRA_START_FROM_MENU"
        const val EXTRA_START_WITH_COMM_CHECK = "EXTRA_START_WITH_COMM_CHECK"
        const val EXTRA_GO_HOME = "EXTRA_GO_HOME"
        const val EXTRA_FORCE_DISCARD = "EXTRA_FORCE_DISCARD"
        const val EXTRA_IS_ALARM_HANDLING = "EXTRA_IS_ALARM_HANDLING"
        const val NORMAL_TEMPERATURE_MIN = 4
        const val NORMAL_TEMPERATURE_MAX = 45

        @JvmStatic
        @JvmOverloads
        fun createIntentForCheckConnection(context: Context, goHomeAfterDiscard: Boolean = true, forceDiscard: Boolean = false, isAlarmHandling: Boolean = false): Intent {
            return Intent(context, EopatchActivity::class.java).apply {
                putExtra(EXTRA_START_PATCH_STEP, PatchStep.CHECK_CONNECTION)
                putExtra(EXTRA_GO_HOME, goHomeAfterDiscard)
                putExtra(EXTRA_FORCE_DISCARD, forceDiscard)
                putExtra(EXTRA_IS_ALARM_HANDLING, isAlarmHandling)
            }
        }

        @JvmStatic
        fun createIntentForChangePatch(context: Context): Intent {
            return createIntent(context, PatchStep.SAFE_DEACTIVATION, false)
        }

        @JvmStatic
        @JvmOverloads
        fun createIntentForDiscarded(context: Context, goHome: Boolean = true): Intent {
            return createIntent(context, PatchStep.DISCARDED_FROM_ALARM, false).apply {
                putExtra(EXTRA_GO_HOME, goHome)
            }
        }

        @JvmStatic
        fun createIntentForCannulaInsertionError(context: Context): Intent {
            return createIntent(context, PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR, false)
        }

        @JvmStatic
        @JvmOverloads
        fun createIntent(context: Context, patchStep: PatchStep, doCommCheck: Boolean = true): Intent {
            return Intent(context, EopatchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                putExtra(EXTRA_START_PATCH_STEP, patchStep)
                putExtra(EXTRA_START_WITH_COMM_CHECK, doCommCheck)
            }
        }

        @JvmStatic
        fun createIntentFromMenu(context: Context, patchStep: PatchStep): Intent {
            return Intent(context, EopatchActivity::class.java).apply {
                putExtra(EXTRA_START_PATCH_STEP, patchStep)
                putExtra(EXTRA_START_FROM_MENU, true)
            }
        }

        @JvmStatic
        fun createIntent(context: Context, lifecycle: PatchLifecycle, doCommCheck: Boolean): Intent? {
            return when (lifecycle) {
                PatchLifecycle.SHUTDOWN -> {
                    // if (PatchConfig().hasMacAddress()) {
                    //     createIntent(context, PatchStep.SAFE_DEACTIVATION, doCommCheck)
                    // } else {
                        createIntent(context, PatchStep.WAKE_UP, false)
                    // }
                }
                PatchLifecycle.BONDED   -> createIntent(context, PatchStep.CONNECT_NEW, doCommCheck)
                PatchLifecycle.REMOVE_NEEDLE_CAP -> createIntent(context, PatchStep.REMOVE_NEEDLE_CAP, doCommCheck)
                PatchLifecycle.REMOVE_PROTECTION_TAPE -> createIntent(context, PatchStep.REMOVE_PROTECTION_TAPE, doCommCheck)
                PatchLifecycle.SAFETY_CHECK -> createIntent(context, PatchStep.SAFETY_CHECK, doCommCheck)
                PatchLifecycle.ROTATE_KNOB -> {
                    // val nextStep = PatchConfig().rotateKnobNeedleSensingError.takeOne(
                    //     PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR, PatchStep.ROTATE_KNOB)
                    // createIntent(context, nextStep, doCommCheck)
                    createIntent(context, PatchStep.ROTATE_KNOB, doCommCheck)
                }
                PatchLifecycle.BASAL_SETTING -> createIntent(context, PatchStep.ROTATE_KNOB, doCommCheck)
                else -> null
            }
        }
    }

    private fun setupViewFragment(baseFragment: EoBaseFragment<*>) {
        replaceFragmentInActivity(baseFragment, R.id.framelayout_fragment, false)
    }
}