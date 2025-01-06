package app.aaps.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.databinding.FragmentEopatchSafeDeativationBinding
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel

class EopatchSafeDeactivationFragment : EoBaseFragment<FragmentEopatchSafeDeativationBinding>() {

    companion object {

        fun newInstance(): EopatchSafeDeactivationFragment = EopatchSafeDeactivationFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_eopatch_safe_deativation

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewModel = ViewModelProvider(requireActivity(), viewModelFactory)[EopatchViewModel::class.java].apply {
            updateExpirationTime()
        }
    }
}