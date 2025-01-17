package app.aaps.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.databinding.FragmentEopatchTurningOffAlarmBinding
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel

class EopatchTurningOffAlarmFragment : EoBaseFragment<FragmentEopatchTurningOffAlarmBinding>() {

    companion object {

        fun newInstance(): EopatchTurningOffAlarmFragment = EopatchTurningOffAlarmFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_eopatch_turning_off_alarm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[EopatchViewModel::class.java]
    }
}