package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.code.PatchStep
import info.nightscout.androidaps.plugins.pump.eopatch.databinding.FragmentEopatchConnectNewBinding
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.ACTIVATION_FAILED
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.BONDING_FAILED
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.GET_PATCH_INFO_FAILED
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.SCAN_FAILED
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.SELF_TEST_FAILED
import info.nightscout.core.ui.toast.ToastUtils

class EopatchConnectNewFragment : EoBaseFragment<FragmentEopatchConnectNewBinding>() {

    companion object {
        fun newInstance(): EopatchConnectNewFragment = EopatchConnectNewFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_eopatch_connect_new

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory).get(EopatchViewModel::class.java)
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