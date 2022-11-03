package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.databinding.FragmentEopatchBasalScheduleBinding
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel

class EopatchBasalScheduleFragment : EoBaseFragment<FragmentEopatchBasalScheduleBinding>() {

    companion object {
        fun newInstance(): EopatchBasalScheduleFragment = EopatchBasalScheduleFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_eopatch_basal_schedule

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory).get(EopatchViewModel::class.java)
            viewModel?.initPatchStep()
        }
    }
}