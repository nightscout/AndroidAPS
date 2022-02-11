package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.databinding.FragmentEopatchTurningOffAlarmBinding
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel

class EopatchTurningOffAlarmFragment : EoBaseFragment<FragmentEopatchTurningOffAlarmBinding>() {

    companion object {
        fun newInstance(): EopatchTurningOffAlarmFragment = EopatchTurningOffAlarmFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_eopatch_turning_off_alarm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewModel = ViewModelProvider(requireActivity(), viewModelFactory).get(EopatchViewModel::class.java)
    }
}