package info.nightscout.pump.medtrum.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.databinding.FragmentMedtrumPrimingBinding
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumViewModel
import javax.inject.Inject

class MedtrumPrimingFragment : MedtrumBaseFragment<FragmentMedtrumPrimingBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper

    companion object {

        fun newInstance(): MedtrumPrimingFragment = MedtrumPrimingFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_priming

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[MedtrumViewModel::class.java]
            viewModel?.apply {
                setupStep.observe(viewLifecycleOwner) {
                    when (it) {
                        MedtrumViewModel.SetupStep.INITIAL,
                        MedtrumViewModel.SetupStep.FILLED,
                        MedtrumViewModel.SetupStep.PRIMING -> Unit // Nothing to do here
                        MedtrumViewModel.SetupStep.PRIMED  -> moveStep(PatchStep.PRIME_COMPLETE)

                        MedtrumViewModel.SetupStep.ERROR   -> {
                            moveStep(PatchStep.ERROR)
                            updateSetupStep(MedtrumViewModel.SetupStep.FILLED) // Reset setup step
                            binding.textWaitForPriming.text = rh.gs(R.string.priming_error)
                            binding.btnNegative.visibility = View.VISIBLE
                            binding.btnPositive.visibility = View.VISIBLE
                        }

                        else                               -> {
                            ToastUtils.errorToast(requireContext(), rh.gs(R.string.unexpected_state, it.toString()))
                            aapsLogger.error(LTag.PUMP, "Unexpected state: $it")
                        }
                    }
                }
                startPrime()
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
