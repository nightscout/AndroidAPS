package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.databinding.FragmentEopatchRemoveNeedleCapBinding
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel

class EopatchRemoveNeedleCapFragment : EoBaseFragment<FragmentEopatchRemoveNeedleCapBinding>() {

    companion object {
        fun newInstance(): EopatchRemoveNeedleCapFragment = EopatchRemoveNeedleCapFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_eopatch_remove_needle_cap

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewModel = ViewModelProvider(requireActivity(), viewModelFactory).get(EopatchViewModel::class.java)
    }
}