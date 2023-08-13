package info.nightscout.pump.medtrum.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.databinding.FragmentMedtrumActivateBinding
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumViewModel
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
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
                            moveStep(PatchStep.ERROR)
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
