package app.aaps.pump.medtrum.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.databinding.FragmentMedtrumStartDeactivationBinding
import app.aaps.pump.medtrum.ui.viewmodel.MedtrumViewModel
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
