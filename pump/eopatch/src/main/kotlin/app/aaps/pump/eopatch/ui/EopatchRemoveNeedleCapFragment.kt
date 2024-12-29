package app.aaps.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.databinding.FragmentEopatchRemoveNeedleCapBinding
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel

class EopatchRemoveNeedleCapFragment : EoBaseFragment<FragmentEopatchRemoveNeedleCapBinding>() {

    companion object {

        fun newInstance(): EopatchRemoveNeedleCapFragment = EopatchRemoveNeedleCapFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_eopatch_remove_needle_cap

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[EopatchViewModel::class.java]
    }
}