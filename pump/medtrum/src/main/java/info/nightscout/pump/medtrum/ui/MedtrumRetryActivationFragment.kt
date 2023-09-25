package info.nightscout.pump.medtrum.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.dialogs.OKDialog
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.databinding.FragmentMedtrumRetryActivationBinding
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumViewModel
import javax.inject.Inject

class MedtrumRetryActivationFragment : MedtrumBaseFragment<FragmentMedtrumRetryActivationBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper

    companion object {

        fun newInstance(): MedtrumRetryActivationFragment = MedtrumRetryActivationFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_retry_activation

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        aapsLogger.debug(LTag.PUMP, "MedtrumRetryActivationFragment onViewCreated")
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[MedtrumViewModel::class.java]
            viewModel?.apply {
                preparePatch() // Use this to make sure we are disconnected at this stage
            }
            btnNegative.setOnClickListener {
                OKDialog.showConfirmation(requireActivity(), rh.gs(R.string.medtrum_deactivate_pump_confirm)) {
                    viewModel?.apply {
                        moveStep(PatchStep.FORCE_DEACTIVATION)
                    }
                }
            }
        }
    }
}
