package info.nightscout.pump.medtrum.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import info.nightscout.pump.medtrum.ui.MedtrumActivateCompleteFragment
import info.nightscout.pump.medtrum.ui.MedtrumActivateFragment
import info.nightscout.pump.medtrum.ui.MedtrumAttachPatchFragment
import info.nightscout.pump.medtrum.ui.MedtrumDeactivatePatchFragment
import info.nightscout.pump.medtrum.ui.MedtrumDeactivationCompleteFragment
import info.nightscout.pump.medtrum.ui.MedtrumPreparePatchConnectFragment
import info.nightscout.pump.medtrum.ui.MedtrumPreparePatchFragment
import info.nightscout.pump.medtrum.ui.MedtrumPrimeCompleteFragment
import info.nightscout.pump.medtrum.ui.MedtrumPrimeFragment
import info.nightscout.pump.medtrum.ui.MedtrumPrimingFragment
import info.nightscout.pump.medtrum.ui.MedtrumStartDeactivationFragment
import info.nightscout.core.utils.extensions.safeGetSerializableExtra
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.pump.medtrum.databinding.ActivityMedtrumBinding
import info.nightscout.pump.medtrum.extension.replaceFragmentInActivity
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumViewModel

class MedtrumActivity : MedtrumBaseActivity<ActivityMedtrumBinding>() {

    override fun getLayoutId(): Int = R.layout.activity_medtrum

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            // TODO
        }

        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.apply {
            viewModel = ViewModelProvider(this@MedtrumActivity, viewModelFactory).get(MedtrumViewModel::class.java)
            viewModel?.apply {
                processIntent(intent)

                patchStep.observe(this@MedtrumActivity) {
                    when (it) {
                        PatchStep.PREPARE_PATCH         -> setupViewFragment(MedtrumPreparePatchFragment.newInstance())
                        PatchStep.PREPARE_PATCH_CONNECT -> setupViewFragment(MedtrumPreparePatchConnectFragment.newInstance())
                        PatchStep.PRIME                 -> setupViewFragment(MedtrumPrimeFragment.newInstance())
                        PatchStep.PRIMING               -> setupViewFragment(MedtrumPrimingFragment.newInstance())
                        PatchStep.PRIME_COMPLETE        -> setupViewFragment(MedtrumPrimeCompleteFragment.newInstance())
                        PatchStep.ATTACH_PATCH          -> setupViewFragment(MedtrumAttachPatchFragment.newInstance())
                        PatchStep.ACTIVATE              -> setupViewFragment(MedtrumActivateFragment.newInstance())
                        PatchStep.ACTIVATE_COMPLETE     -> setupViewFragment(MedtrumActivateCompleteFragment.newInstance())
                        PatchStep.COMPLETE              -> this@MedtrumActivity.finish()
                        PatchStep.ERROR                 -> Unit // Do nothing, let activity handle this

                        PatchStep.CANCEL                -> {
                            if (setupStep.value !in listOf(MedtrumViewModel.SetupStep.ACTIVATED, MedtrumViewModel.SetupStep.START_DEACTIVATION, MedtrumViewModel.SetupStep.STOPPED)) {
                                resetPumpState()
                            }
                            this@MedtrumActivity.finish()
                        }

                        PatchStep.START_DEACTIVATION    -> setupViewFragment(MedtrumStartDeactivationFragment.newInstance())
                        PatchStep.DEACTIVATE            -> setupViewFragment(MedtrumDeactivatePatchFragment.newInstance())

                        PatchStep.FORCE_DEACTIVATION    -> {
                            medtrumPump.pumpState = MedtrumPumpState.STOPPED
                            moveStep(PatchStep.DEACTIVATION_COMPLETE)
                        }

                        PatchStep.DEACTIVATION_COMPLETE -> setupViewFragment(MedtrumDeactivationCompleteFragment.newInstance())
                        null                            -> Unit
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {
        binding.viewModel?.apply {
            intent?.run {
                val step = intent.safeGetSerializableExtra(EXTRA_START_PATCH_STEP, PatchStep::class.java)
                if (step != null) {
                    initializePatchStep(step)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO
    }

    override fun onBackPressed() {
        binding.viewModel?.apply {
            // TODO DEACTIVATION ?
        }
    }

    companion object {

        const val EXTRA_START_PATCH_STEP = "EXTRA_START_PATCH_FRAGMENT_UI"
        const val EXTRA_START_FROM_MENU = "EXTRA_START_FROM_MENU"

        @JvmStatic fun createIntentFromMenu(context: Context, patchStep: PatchStep): Intent {
            return Intent(context, MedtrumActivity::class.java).apply {
                putExtra(EXTRA_START_PATCH_STEP, patchStep)
                putExtra(EXTRA_START_FROM_MENU, true)
            }
        }

    }

    private fun setupViewFragment(baseFragment: MedtrumBaseFragment<*>) {
        replaceFragmentInActivity(baseFragment, R.id.framelayout_fragment, false)
    }

}
