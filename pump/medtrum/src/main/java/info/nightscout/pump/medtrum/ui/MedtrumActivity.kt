package info.nightscout.pump.medtrum.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.plugins.pump.eopatch.ui.MedtrumActivateFragment
import info.nightscout.androidaps.plugins.pump.eopatch.ui.MedtrumAttachPatchFragment
import info.nightscout.androidaps.plugins.pump.eopatch.ui.MedtrumDeactivatePatchFragment
import info.nightscout.androidaps.plugins.pump.eopatch.ui.MedtrumPreparePatchFragment
import info.nightscout.androidaps.plugins.pump.eopatch.ui.MedtrumPrimeFragment
import info.nightscout.androidaps.plugins.pump.eopatch.ui.MedtrumStartDeactivationFragment
import info.nightscout.core.utils.extensions.safeGetSerializableExtra
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.PatchStep
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

        binding.apply {
            viewModel = ViewModelProvider(this@MedtrumActivity, viewModelFactory).get(MedtrumViewModel::class.java)
            viewModel?.apply {
                processIntent(intent)

                patchStep.observe(this@MedtrumActivity) {
                    when (it) {
                        PatchStep.PREPARE_PATCH         -> setupViewFragment(MedtrumPreparePatchFragment.newInstance())
                        PatchStep.PRIME                 -> setupViewFragment(MedtrumPrimeFragment.newInstance())
                        PatchStep.ATTACH_PATCH          -> setupViewFragment(MedtrumAttachPatchFragment.newInstance())
                        PatchStep.ACTIVATE              -> setupViewFragment(MedtrumActivateFragment.newInstance())
                        PatchStep.COMPLETE              -> this@MedtrumActivity.finish() // TODO proper finish
                        PatchStep.CANCEL                -> this@MedtrumActivity.finish()
                        PatchStep.START_DEACTIVATION    -> setupViewFragment(MedtrumStartDeactivationFragment.newInstance())
                        PatchStep.DEACTIVATE            -> setupViewFragment(MedtrumDeactivatePatchFragment.newInstance())
                        PatchStep.DEACTIVATION_COMPLETE -> this@MedtrumActivity.finish() // TODO proper finish
                        else                            -> Unit
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
                initializePatchStep(step)
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
