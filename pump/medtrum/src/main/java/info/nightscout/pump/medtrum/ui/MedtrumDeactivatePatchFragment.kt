package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.databinding.FragmentMedtrumDeactivatePatchBinding
import info.nightscout.pump.medtrum.ui.MedtrumBaseFragment
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumViewModel
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class MedtrumDeactivatePatchFragment : MedtrumBaseFragment<FragmentMedtrumDeactivatePatchBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger

    companion object {

        fun newInstance(): MedtrumDeactivatePatchFragment = MedtrumDeactivatePatchFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_deactivate_patch

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        aapsLogger.debug(LTag.PUMP, "MedtrumDeactivatePatchFragment onViewCreated")
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory).get(MedtrumViewModel::class.java)
            viewModel?.apply {
                setupStep.observe(viewLifecycleOwner) {
                    when (it) {
                        MedtrumViewModel.SetupStep.STOPPED -> btnPositive.visibility = View.VISIBLE

                        MedtrumViewModel.SetupStep.ERROR   -> {
                            ToastUtils.errorToast(requireContext(), "Error deactivate") // TODO: String resource and show error message
                            moveStep(PatchStep.CANCEL)
                        }

                        else                               -> Unit // Nothing to do here
                    }
                }
                deactivatePatch()
            }
        }
    }
}
