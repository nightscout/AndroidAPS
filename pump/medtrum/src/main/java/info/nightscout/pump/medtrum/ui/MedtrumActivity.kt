package info.nightscout.pump.medtrum.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import info.nightscout.core.utils.extensions.safeGetSerializableExtra
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.pump.medtrum.databinding.ActivityMedtrumBinding
import info.nightscout.pump.medtrum.extension.replaceFragmentInActivity
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumViewModel

class MedtrumActivity : MedtrumBaseActivity<ActivityMedtrumBinding>() {

    override fun getLayoutId(): Int = R.layout.activity_medtrum

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        title = getString(R.string.change_patch_label)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.apply {
            viewModel = ViewModelProvider(this@MedtrumActivity, viewModelFactory)[MedtrumViewModel::class.java]
            viewModel?.apply {
                processIntent(intent)

                patchStep.observe(this@MedtrumActivity) {
                    when (it) {
                        PatchStep.PREPARE_PATCH            -> setupViewFragment(MedtrumPreparePatchFragment.newInstance())
                        PatchStep.PREPARE_PATCH_CONNECT    -> setupViewFragment(MedtrumPreparePatchConnectFragment.newInstance())
                        PatchStep.PRIME                    -> setupViewFragment(MedtrumPrimeFragment.newInstance())
                        PatchStep.PRIMING                  -> setupViewFragment(MedtrumPrimingFragment.newInstance())
                        PatchStep.PRIME_COMPLETE           -> setupViewFragment(MedtrumPrimeCompleteFragment.newInstance())
                        PatchStep.ATTACH_PATCH             -> setupViewFragment(MedtrumAttachPatchFragment.newInstance())
                        PatchStep.ACTIVATE                 -> setupViewFragment(MedtrumActivateFragment.newInstance())
                        PatchStep.ACTIVATE_COMPLETE        -> setupViewFragment(MedtrumActivateCompleteFragment.newInstance())
                        PatchStep.ERROR                    -> Unit // Do nothing, let activity handle this
                        PatchStep.RETRY_ACTIVATION         -> setupViewFragment(MedtrumRetryActivationFragment.newInstance())
                        PatchStep.RETRY_ACTIVATION_CONNECT -> setupViewFragment(MedtrumRetryActivationConnectFragment.newInstance())
                        PatchStep.START_DEACTIVATION       -> setupViewFragment(MedtrumStartDeactivationFragment.newInstance())
                        PatchStep.DEACTIVATE               -> setupViewFragment(MedtrumDeactivatePatchFragment.newInstance())

                        PatchStep.CANCEL                   -> {
                            handleCancel()
                            this@MedtrumActivity.finish()
                        }

                        PatchStep.COMPLETE                 -> {
                            handleComplete()
                            this@MedtrumActivity.finish()
                        }

                        PatchStep.FORCE_DEACTIVATION       -> {
                            medtrumPump.pumpState = MedtrumPumpState.STOPPED
                            moveStep(PatchStep.DEACTIVATION_COMPLETE)
                        }

                        PatchStep.DEACTIVATION_COMPLETE    -> setupViewFragment(MedtrumDeactivationCompleteFragment.newInstance())
                        null                               -> Unit
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                binding.viewModel?.apply {
                    when (patchStep.value) {
                        PatchStep.PREPARE_PATCH,
                        PatchStep.START_DEACTIVATION,
                        PatchStep.RETRY_ACTIVATION      -> {
                            handleCancel()
                            this@MedtrumActivity.finish()
                        }

                        PatchStep.COMPLETE,
                        PatchStep.DEACTIVATION_COMPLETE -> {
                            handleComplete()
                            this@MedtrumActivity.finish()
                        }

                        else                            -> Unit
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
                if (step != null) {
                    initializePatchStep(step)
                }
            }
        }
    }

    companion object {

        const val EXTRA_START_PATCH_STEP = "EXTRA_START_PATCH_FRAGMENT_UI"
        private const val EXTRA_START_FROM_MENU = "EXTRA_START_FROM_MENU"

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
