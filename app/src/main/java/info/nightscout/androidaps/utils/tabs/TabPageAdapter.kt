package info.nightscout.androidaps.utils.tabs

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import info.nightscout.androidaps.interfaces.PluginBase
import java.util.*

class TabPageAdapter(private val activity: AppCompatActivity) : FragmentStateAdapter(activity) {

    private val visibleFragmentList = ArrayList<PluginBase>()

    override fun getItemCount(): Int = visibleFragmentList.size
    override fun createFragment(position: Int): Fragment =
        activity.supportFragmentManager.fragmentFactory.instantiate(ClassLoader.getSystemClassLoader(), visibleFragmentList[position].pluginDescription.fragmentClass)

    fun getPluginAt(position: Int): PluginBase = visibleFragmentList[position]

    fun registerNewFragment(plugin: PluginBase) {
        if (plugin.hasFragment() && plugin.isFragmentVisible()) {
            visibleFragmentList.add(plugin)
        }
    }
}