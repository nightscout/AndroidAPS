package info.nightscout.pump.medtrum.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.databinding.FragmentMedtrumRetryActivationConnectBinding
import info.nightscout.pump.medtrum.ui.MedtrumBaseFragment
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumViewModel
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import javax.inject.Inject

class MedtrumRetryActivationConnectFragment : MedtrumBaseFragment<FragmentMedtrumRetryActivationConnectBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper

    companion object {

        fun newInstance(): MedtrumRetryActivationConnectFragment = MedtrumRetryActivationConnectFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_retry_activation_connect

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        aapsLogger.debug(LTag.PUMP, "MedtrumRetryActivationConnectFragment onViewCreated")
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[MedtrumViewModel::class.java]
            viewModel?.apply {

                setupStep.observe(viewLifecycleOwner) {
                    when (it) {
                        MedtrumViewModel.SetupStep.INITIAL   -> Unit // Nothing to do here
                        MedtrumViewModel.SetupStep.FILLED    -> forceMoveStep(PatchStep.PRIME)
                        MedtrumViewModel.SetupStep.PRIMING   -> forceMoveStep(PatchStep.PRIMING)
                        MedtrumViewModel.SetupStep.PRIMED    -> forceMoveStep(PatchStep.PRIME_COMPLETE)
                        MedtrumViewModel.SetupStep.ACTIVATED -> forceMoveStep(PatchStep.ACTIVATE_COMPLETE)

                        else                                 -> {
                            aapsLogger.error(LTag.PUMP, "Unexpected state: $it")
                        }
                    }
                }
                retryActivationConnect()
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
