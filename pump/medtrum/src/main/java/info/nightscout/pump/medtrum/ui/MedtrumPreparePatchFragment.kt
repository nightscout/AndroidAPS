package info.nightscout.pump.medtrum.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.databinding.FragmentMedtrumPreparePatchBinding
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumViewModel
import javax.inject.Inject

class MedtrumPreparePatchFragment : MedtrumBaseFragment<FragmentMedtrumPreparePatchBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger

    companion object {

        fun newInstance(): MedtrumPreparePatchFragment = MedtrumPreparePatchFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_prepare_patch

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        aapsLogger.debug(LTag.PUMP, "MedtrumPreparePatchFragment onViewCreated")
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[MedtrumViewModel::class.java]
            viewModel?.apply {
                preparePatch()
            }
        }
    }
}
