package app.aaps.plugins.main.general.nfcCommands

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class NfcPagerAdapter(
    fragment: Fragment,
) : FragmentStateAdapter(fragment) {
    val logFragment = NfcLogFragment()
    val tagsFragment = NfcTagsFragment()

    override fun getItemCount() = 2

    override fun createFragment(position: Int): Fragment =
        when (position) {
            0 -> logFragment
            else -> tagsFragment
        }
}
