package app.aaps.pump.eopatch.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.pump.eopatch.code.PatchLifecycle
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.compose.EopatchPatchScreen
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel
import app.aaps.pump.eopatch.compose.PatchEvent
import app.aaps.pump.eopatch.di.EopatchPluginQualifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity retained for alarm-driven external entry points (AlarmProcess, ActivationNotCompleteDialog).
 * The main UI entry point is now via EopatchComposeContent in the plugin.
 */
class EopatchActivity : TranslatedDaggerAppCompatActivity() {

    @EopatchPluginQualifier
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        val patchViewModel = ViewModelProvider(this, viewModelFactory)[EopatchPatchViewModel::class.java]

        // Process intent
        val step = intent?.getSerializableExtra(EXTRA_START_PATCH_STEP) as? PatchStep
        patchViewModel.forceDiscard = intent?.getBooleanExtra(EXTRA_FORCE_DISCARD, false) ?: false
        patchViewModel.isInAlarmHandling = intent?.getBooleanExtra(EXTRA_IS_ALARM_HANDLING, false) ?: false

        if (intent?.getBooleanExtra(EXTRA_START_WITH_COMM_CHECK, false) == true) {
            patchViewModel.checkCommunication(
                onSuccessListener = { patchViewModel.initializePatchStep(step) },
                onCancelListener = {
                    setResult(RESULT_CANCELED)
                    finish()
                },
                onDiscardListener = {
                    setResult(RESULT_DISCARDED)
                    finish()
                },
                doPreCheck = true
            )
        } else {
            patchViewModel.initializePatchStep(step)
        }

        // Observe finish events
        scope.launch {
            patchViewModel.events.collect { event ->
                when (event) {
                    is PatchEvent.Finish -> finish()
                    else                 -> Unit
                }
            }
        }

        setContent {
            AapsTheme {
                EopatchPatchScreen(viewModel = patchViewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
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

        fun createIntentForCheckConnection(context: Context, goHomeAfterDiscard: Boolean = true, forceDiscard: Boolean = false, isAlarmHandling: Boolean = false): Intent {
            return Intent(context, EopatchActivity::class.java).apply {
                putExtra(EXTRA_START_PATCH_STEP, PatchStep.CHECK_CONNECTION)
                putExtra(EXTRA_START_WITH_COMM_CHECK, true)
                putExtra(EXTRA_GO_HOME, goHomeAfterDiscard)
                putExtra(EXTRA_FORCE_DISCARD, forceDiscard)
                putExtra(EXTRA_IS_ALARM_HANDLING, isAlarmHandling)
            }
        }

        fun createIntentForChangePatch(context: Context): Intent {
            return createIntent(context, PatchStep.SAFE_DEACTIVATION, false)
        }

        fun createIntentForDiscarded(context: Context, goHome: Boolean = true): Intent {
            return createIntent(context, PatchStep.DISCARDED_FROM_ALARM, false).apply {
                putExtra(EXTRA_GO_HOME, goHome)
            }
        }

        fun createIntentForCannulaInsertionError(context: Context): Intent {
            return createIntent(context, PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR, false)
        }

        fun createIntent(context: Context, patchStep: PatchStep, doCommCheck: Boolean = true): Intent {
            return Intent(context, EopatchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                putExtra(EXTRA_START_PATCH_STEP, patchStep)
                putExtra(EXTRA_START_WITH_COMM_CHECK, doCommCheck)
            }
        }

        fun createIntentFromMenu(context: Context, patchStep: PatchStep): Intent {
            return Intent(context, EopatchActivity::class.java).apply {
                putExtra(EXTRA_START_PATCH_STEP, patchStep)
                putExtra(EXTRA_START_FROM_MENU, true)
            }
        }

        fun createIntent(context: Context, lifecycle: PatchLifecycle, doCommCheck: Boolean): Intent? {
            return when (lifecycle) {
                PatchLifecycle.SHUTDOWN               -> createIntent(context, PatchStep.WAKE_UP, false)
                PatchLifecycle.BONDED                 -> createIntent(context, PatchStep.CONNECT_NEW, doCommCheck)
                PatchLifecycle.REMOVE_NEEDLE_CAP      -> createIntent(context, PatchStep.REMOVE_NEEDLE_CAP, doCommCheck)
                PatchLifecycle.REMOVE_PROTECTION_TAPE -> createIntent(context, PatchStep.REMOVE_PROTECTION_TAPE, doCommCheck)
                PatchLifecycle.SAFETY_CHECK           -> createIntent(context, PatchStep.SAFETY_CHECK, doCommCheck)
                PatchLifecycle.ROTATE_KNOB            -> createIntent(context, PatchStep.ROTATE_KNOB, doCommCheck)
                PatchLifecycle.BASAL_SETTING          -> createIntent(context, PatchStep.ROTATE_KNOB, doCommCheck)
                else                                  -> null
            }
        }
    }
}
