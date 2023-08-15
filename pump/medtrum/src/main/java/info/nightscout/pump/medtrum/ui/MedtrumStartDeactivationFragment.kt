package info.nightscout.pump.medtrum.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.databinding.FragmentMedtrumStartDeactivationBinding
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumViewModel
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import javax.inject.Inject

class MedtrumStartDeactivationFragment : MedtrumBaseFragment<FragmentMedtrumStartDeactivationBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper

    companion object {

        fun newInstance(): MedtrumStartDeactivationFragment = MedtrumStartDeactivationFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_start_deactivation

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        aapsLogger.debug(LTag.PUMP, "MedtrumStartDeactivationFragment onViewCreated")
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[MedtrumViewModel::class.java]
            viewModel?.apply {
                updateSetupStep(MedtrumViewModel.SetupStep.START_DEACTIVATION)
            }
            btnPositive.setOnClickListener {
                OKDialog.showConfirmation(requireActivity(), rh.gs(R.string.medtrum_deactivate_pump_confirm)) {
                    viewModel?.apply {
                        moveStep(PatchStep.DEACTIVATE)
                    }
                }
            }
        }
    }
}
