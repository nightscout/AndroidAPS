package app.aaps.pump.medtrum.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.databinding.FragmentMedtrumDeactivationCompleteBinding
import app.aaps.pump.medtrum.ui.viewmodel.MedtrumViewModel
import javax.inject.Inject

class MedtrumDeactivationCompleteFragment : MedtrumBaseFragment<FragmentMedtrumDeactivationCompleteBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger

    companion object {

        fun newInstance(): MedtrumDeactivationCompleteFragment = MedtrumDeactivationCompleteFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_deactivation_complete

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        aapsLogger.debug(LTag.PUMP, "MedtrumStartDeactivationFragment onViewCreated")
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[MedtrumViewModel::class.java]
            viewModel?.apply {
                // Nothing to do here (yet)
            }
        }
    }
}
