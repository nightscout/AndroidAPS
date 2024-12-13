package app.aaps.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.databinding.FragmentEopatchConnectNewBinding
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.ACTIVATION_FAILED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.BONDING_FAILED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.GET_PATCH_INFO_FAILED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.SCAN_FAILED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.SELF_TEST_FAILED

class EopatchConnectNewFragment : EoBaseFragment<FragmentEopatchConnectNewBinding>() {

    companion object {

        fun newInstance(): EopatchConnectNewFragment = EopatchConnectNewFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_eopatch_connect_new

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[EopatchViewModel::class.java]
            viewModel?.apply {
                setupStep.observe(viewLifecycleOwner) {
                    when (it) {
                        SCAN_FAILED,
                        BONDING_FAILED        -> checkCommunication({ retryScan() }, { moveStep(PatchStep.WAKE_UP) })

                        GET_PATCH_INFO_FAILED -> checkCommunication({ getPatchInfo() }, { moveStep(PatchStep.WAKE_UP) })
                        SELF_TEST_FAILED      -> checkCommunication({ selfTest() }, { moveStep(PatchStep.WAKE_UP) })
                        ACTIVATION_FAILED     -> ToastUtils.errorToast(requireContext(), "Activation failed!")
                        else                  -> Unit
                    }
                }

                startScan()
            }
        }
    }
}