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
import app.aaps.pump.medtrum.databinding.FragmentMedtrumPreparePatchConnectBinding
import app.aaps.pump.medtrum.ui.viewmodel.MedtrumViewModel
import javax.inject.Inject

class MedtrumPreparePatchConnectFragment : MedtrumBaseFragment<FragmentMedtrumPreparePatchConnectBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper

    companion object {

        fun newInstance(): MedtrumPreparePatchConnectFragment = MedtrumPreparePatchConnectFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_prepare_patch_connect

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        aapsLogger.debug(LTag.PUMP, "MedtrumPreparePatchFragment onViewCreated")
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[MedtrumViewModel::class.java]
            viewModel?.apply {
                setupStep.observe(viewLifecycleOwner) {
                    when (it) {
                        MedtrumViewModel.SetupStep.INITIAL,
                        MedtrumViewModel.SetupStep.STOPPED -> btnPositive.visibility = View.GONE

                        MedtrumViewModel.SetupStep.FILLED  -> btnPositive.visibility = View.VISIBLE

                        else                               -> {
                            aapsLogger.error(LTag.PUMP, "Unexpected state: $it")
                            OKDialog.show(
                                requireActivity(),
                                rh.gs(app.aaps.core.ui.R.string.error),
                                rh.gs(R.string.unexpected_state, it.toString()),
                                runOnDismiss = true
                            ) {
                                viewModel?.moveStep(PatchStep.CANCEL)
                            }
                        }
                    }
                }
                preparePatchConnect()
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
