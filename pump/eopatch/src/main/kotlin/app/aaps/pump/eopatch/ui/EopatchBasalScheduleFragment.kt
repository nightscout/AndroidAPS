package app.aaps.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.databinding.FragmentEopatchBasalScheduleBinding
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel

class EopatchBasalScheduleFragment : EoBaseFragment<FragmentEopatchBasalScheduleBinding>() {

    companion object {

        fun newInstance(): EopatchBasalScheduleFragment = EopatchBasalScheduleFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_eopatch_basal_schedule

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[EopatchViewModel::class.java]
            viewModel?.initPatchStep()
        }
    }
}