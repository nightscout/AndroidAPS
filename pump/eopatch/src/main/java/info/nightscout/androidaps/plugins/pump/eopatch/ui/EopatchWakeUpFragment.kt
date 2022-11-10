package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.databinding.FragmentEopatchWakeUpBinding
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel

class EopatchWakeUpFragment : EoBaseFragment<FragmentEopatchWakeUpBinding>() {

    companion object {
        fun newInstance(): EopatchWakeUpFragment = EopatchWakeUpFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_eopatch_wake_up

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory).get(EopatchViewModel::class.java)
            viewModel?.initPatchStep()
        }
    }
}