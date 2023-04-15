package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.databinding.FragmentMedtrumStartDeactivationBinding
import info.nightscout.pump.medtrum.ui.MedtrumBaseFragment
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumViewModel
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class MedtrumStartDeactivationFragment : MedtrumBaseFragment<FragmentMedtrumStartDeactivationBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger

    companion object {

        fun newInstance(): MedtrumStartDeactivationFragment = MedtrumStartDeactivationFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_start_deactivation

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        aapsLogger.debug(LTag.PUMP, "MedtrumStartDeactivationFragment onViewCreated")
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory).get(MedtrumViewModel::class.java)
            viewModel?.apply {
                setupStep.observe(viewLifecycleOwner) {
                    when (it) {
                        MedtrumViewModel.SetupStep.READY_DEACTIVATE -> btnPositive.visibility = View.VISIBLE

                        MedtrumViewModel.SetupStep.ERROR            -> {
                            ToastUtils.errorToast(requireContext(), "Error deactivate") // TODO: String resource and show error message
                            moveStep(PatchStep.CANCEL)
                        }

                        else                                        -> Unit // Nothing to do here
                    }
                }
                startDeactivation()
            }
        }
    }
}
