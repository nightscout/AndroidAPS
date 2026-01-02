package app.aaps.pump.common.hw.rileylink.dialog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.pump.common.hw.rileylink.R
import app.aaps.pump.common.hw.rileylink.databinding.RileylinkStatusBinding
import com.google.android.material.tabs.TabLayoutMediator
import javax.inject.Inject

class RileyLinkStatusActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var rh: ResourceHelper

    private lateinit var binding: RileylinkStatusBinding

    private var sectionsPagerAdapter: SectionsPagerAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RileylinkStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sectionsPagerAdapter = SectionsPagerAdapter(this)
        sectionsPagerAdapter?.addFragment(RileyLinkStatusGeneralFragment::class.java.name, rh.gs(R.string.rileylink_settings_tab1))
        sectionsPagerAdapter?.addFragment(RileyLinkStatusHistoryFragment::class.java.name, rh.gs(R.string.rileylink_settings_tab2))

        binding.pager.adapter = sectionsPagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
            tab.text = sectionsPagerAdapter?.getPageTitle(position)
        }.attach()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.pager.adapter = null
        binding.tabLayout.clearOnTabSelectedListeners()
    }

    class SectionsPagerAdapter(private val activity: AppCompatActivity) : FragmentStateAdapter(activity) {

        private val fragmentList: MutableList<String> = ArrayList()
        private val fragmentTitle: MutableList<String> = ArrayList()
        override fun getItemCount(): Int = fragmentList.size
        override fun createFragment(position: Int): Fragment =
            activity.supportFragmentManager.fragmentFactory.instantiate(ClassLoader.getSystemClassLoader(), fragmentList[position])

        fun getPageTitle(position: Int): CharSequence = fragmentTitle[position]
        fun addFragment(fragment: String, title: String) {
            fragmentList.add(fragment)
            fragmentTitle.add(title)
        }
    }
}