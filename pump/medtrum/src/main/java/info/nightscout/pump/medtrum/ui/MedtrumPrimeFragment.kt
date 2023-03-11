package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.databinding.FragmentMedtrumPrimeBinding
import info.nightscout.pump.medtrum.ui.MedtrumBaseFragment
import info.nightscout.pump.medtrum.ui.viewmodel.MedtrumViewModel
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class MedtrumPrimeFragment : MedtrumBaseFragment<FragmentMedtrumPrimeBinding>() {

    @Inject lateinit var aapsLogger: AAPSLogger

    companion object {

        fun newInstance(): MedtrumPrimeFragment = MedtrumPrimeFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_medtrum_prime

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory).get(MedtrumViewModel::class.java)
            // TODO do stuff
        }
    }
}
