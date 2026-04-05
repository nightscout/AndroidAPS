package app.aaps.plugins.main.general.nfcCommands

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.NfccommandsFragmentBinding
import com.google.android.material.tabs.TabLayoutMediator
import dagger.android.support.DaggerFragment

class NfcCommandsFragment : DaggerFragment() {
    private var bindingOrNull: NfccommandsFragmentBinding? = null
    private val binding get() = bindingOrNull!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingOrNull = NfccommandsFragmentBinding.inflate(inflater, container, false)

        val pagerAdapter = NfcPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text =
                when (position) {
                    0 -> getString(R.string.nfccommands_tab_log)
                    else -> getString(R.string.nfccommands_tab_my_tags)
                }
        }.attach()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingOrNull = null
    }
}
