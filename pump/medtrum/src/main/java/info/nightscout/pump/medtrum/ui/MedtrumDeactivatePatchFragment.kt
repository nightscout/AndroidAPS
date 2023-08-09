package info.nightscout.pump.medtrum.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.databinding.FragmentMedtrumDeactivatePatchBinding
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumViewModel
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import javax.inject.Inject

class MedtrumDeactivatePatchFragment : MedtrumBaseFragment<FragmentMedtrumDeactivatePatchBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper

    companion object {

        fun newInstance(): MedtrumDeactivatePatchFragment = MedtrumDeactivatePatchFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_deactivate_patch

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        aapsLogger.debug(LTag.PUMP, "MedtrumDeactivatePatchFragment onViewCreated")
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[MedtrumViewModel::class.java]
            viewModel?.apply {
                setupStep.observe(viewLifecycleOwner) {
                    when (it) {
                        MedtrumViewModel.SetupStep.STOPPED -> {
                            moveStep(PatchStep.DEACTIVATION_COMPLETE)
                        }

                        MedtrumViewModel.SetupStep.ERROR   -> {
                            moveStep(PatchStep.ERROR)
                            updateSetupStep(MedtrumViewModel.SetupStep.START_DEACTIVATION) // Reset setup step
                            binding.textDeactivatingPump.text = rh.gs(R.string.deactivating_error)
                            binding.btnNegative.visibility = View.VISIBLE
                            binding.btnPositive.visibility = View.VISIBLE
                        }

                        else                               -> Unit // Nothing to do here
                    }
                }
                deactivatePatch()
            }
            btnNegative.setOnClickListener {
                OKDialog.showConfirmation(requireActivity(), rh.gs(R.string.cancel_sure)) {
                    viewModel?.apply {
                        moveStep(PatchStep.CANCEL)
                    }
                }
            }
        }
    }
}
