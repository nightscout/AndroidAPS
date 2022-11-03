package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.code.PatchStep
import info.nightscout.androidaps.plugins.pump.eopatch.databinding.FragmentEopatchSafetyCheckBinding
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.*

class EopatchSafetyCheckFragment : EoBaseFragment<FragmentEopatchSafetyCheckBinding>() {

    companion object {
        fun newInstance(): EopatchSafetyCheckFragment = EopatchSafetyCheckFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_eopatch_safety_check

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory).get(EopatchViewModel::class.java)
            viewModel?.apply {
                initPatchStep()

                setupStep.observe(viewLifecycleOwner) {
                    when (it) {
                        SAFETY_CHECK_FAILED -> checkCommunication({ retrySafetyCheck() }, { moveStep(PatchStep.SAFETY_CHECK) })
                        else                -> Unit
                    }
                }

                startSafetyCheck()
            }
        }
    }
}