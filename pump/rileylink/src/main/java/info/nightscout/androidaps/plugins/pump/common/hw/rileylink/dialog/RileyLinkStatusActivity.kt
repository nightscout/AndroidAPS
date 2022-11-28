package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.R
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.databinding.RileylinkStatusBinding
import info.nightscout.shared.interfaces.ResourceHelper
import javax.inject.Inject

class RileyLinkStatusActivity : DaggerAppCompatActivity() {

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