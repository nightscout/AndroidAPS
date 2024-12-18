package app.aaps.pump.medtrum.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.databinding.FragmentMedtrumActivateBinding
import app.aaps.pump.medtrum.ui.viewmodel.MedtrumViewModel
import javax.inject.Inject

class MedtrumActivateFragment : MedtrumBaseFragment<FragmentMedtrumActivateBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper

    companion object {

        fun newInstance(): MedtrumActivateFragment = MedtrumActivateFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_activate

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[MedtrumViewModel::class.java]
            viewModel?.apply {
                setupStep.observe(viewLifecycleOwner) {
                    when (it) {
                        MedtrumViewModel.SetupStep.INITIAL,
                        MedtrumViewModel.SetupStep.PRIMED    -> Unit // Nothing to do here, previous state
                        MedtrumViewModel.SetupStep.ACTIVATED -> moveStep(PatchStep.ACTIVATE_COMPLETE)

                        MedtrumViewModel.SetupStep.ERROR     -> {
                            updateSetupStep(MedtrumViewModel.SetupStep.PRIMED) // Reset setup step
                            binding.textActivatingPump.text = rh.gs(R.string.activating_error)
                            binding.btnPositive.visibility = View.VISIBLE
                        }

                        else                                 -> {
                            ToastUtils.errorToast(requireContext(), "Unexpected state: $it")
                            aapsLogger.error(LTag.PUMP, "Unexpected state: $it")
                        }
                    }
                }
                startActivate()
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
