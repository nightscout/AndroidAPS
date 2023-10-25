package app.aaps.ui.tabs

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginFragment

class TabPageAdapter(private val activity: AppCompatActivity) : FragmentStateAdapter(activity) {

    private val visibleFragmentList = ArrayList<PluginBase>()

    override fun getItemCount(): Int = visibleFragmentList.size
    override fun createFragment(position: Int): Fragment =
        activity.supportFragmentManager.fragmentFactory.instantiate(ClassLoader.getSystemClassLoader(), visibleFragmentList[position].pluginDescription.fragmentClass ?: Fragment::class.java.name)
            .also { if (it is PluginFragment) it.plugin = getPluginAt(position) }

    fun getPluginAt(position: Int): PluginBase = visibleFragmentList[position]

    fun registerNewFragment(plugin: PluginBase) {
        if (plugin.hasFragment() && plugin.isFragmentVisible()) {
            visibleFragmentList.add(plugin)
        }
    }
}