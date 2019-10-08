package info.nightscout.androidaps.plugins.configBuilder


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.PasswordProtection
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.configbuilder_fragment.*
import java.util.*

class ConfigBuilderFragment : Fragment() {

    private var disposable: CompositeDisposable = CompositeDisposable()
    private val pluginViewHolders = ArrayList<PluginViewHolder>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.configbuilder_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (PasswordProtection.isLocked("settings_password"))
            configbuilder_main_layout.visibility = View.GONE
        else
            unlock.visibility = View.GONE

        unlock.setOnClickListener {
            PasswordProtection.QueryPassword(context, R.string.settings_password, "settings_password", {
                configbuilder_main_layout.visibility = View.VISIBLE
                unlock.visibility = View.GONE
            }, null)
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(RxBus
                .toObservable(EventConfigBuilderUpdateGui::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    for (pluginViewHolder in pluginViewHolders) pluginViewHolder.update()
                }, {
                    FabricPrivacy.logException(it)
                }))
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    private fun updateGUI() {
        configbuilder_categories.removeAllViews()
        createViewsForPlugins(R.string.configbuilder_profile, R.string.configbuilder_profile_description, PluginType.PROFILE, MainApp.getSpecificPluginsVisibleInListByInterface(ProfileInterface::class.java, PluginType.PROFILE))
        createViewsForPlugins(R.string.configbuilder_insulin, R.string.configbuilder_insulin_description, PluginType.INSULIN, MainApp.getSpecificPluginsVisibleInListByInterface(InsulinInterface::class.java, PluginType.INSULIN))
        createViewsForPlugins(R.string.configbuilder_bgsource, R.string.configbuilder_bgsource_description, PluginType.BGSOURCE, MainApp.getSpecificPluginsVisibleInListByInterface(BgSourceInterface::class.java, PluginType.BGSOURCE))
        createViewsForPlugins(R.string.configbuilder_pump, R.string.configbuilder_pump_description, PluginType.PUMP, MainApp.getSpecificPluginsVisibleInList(PluginType.PUMP))
        createViewsForPlugins(R.string.configbuilder_sensitivity, R.string.configbuilder_sensitivity_description, PluginType.SENSITIVITY, MainApp.getSpecificPluginsVisibleInListByInterface(SensitivityInterface::class.java, PluginType.SENSITIVITY))
        createViewsForPlugins(R.string.configbuilder_aps, R.string.configbuilder_aps_description, PluginType.APS, MainApp.getSpecificPluginsVisibleInList(PluginType.APS))
        createViewsForPlugins(R.string.configbuilder_loop, R.string.configbuilder_loop_description, PluginType.LOOP, MainApp.getSpecificPluginsVisibleInList(PluginType.LOOP))
        createViewsForPlugins(R.string.constraints, R.string.configbuilder_constraints_description, PluginType.CONSTRAINTS, MainApp.getSpecificPluginsVisibleInListByInterface(ConstraintsInterface::class.java, PluginType.CONSTRAINTS))
        createViewsForPlugins(R.string.configbuilder_treatments, R.string.configbuilder_treatments_description, PluginType.TREATMENT, MainApp.getSpecificPluginsVisibleInList(PluginType.TREATMENT))
        createViewsForPlugins(R.string.configbuilder_general, R.string.configbuilder_general_description, PluginType.GENERAL, MainApp.getSpecificPluginsVisibleInList(PluginType.GENERAL))
    }

    private fun createViewsForPlugins(@StringRes title: Int, @StringRes description: Int, pluginType: PluginType, plugins: List<PluginBase>) {
        if (plugins.size == 0) return
        val parent = layoutInflater.inflate(R.layout.configbuilder_single_category, null) as LinearLayout
        (parent.findViewById<View>(R.id.category_title) as TextView).text = MainApp.gs(title)
        (parent.findViewById<View>(R.id.category_description) as TextView).text = MainApp.gs(description)
        val pluginContainer = parent.findViewById<LinearLayout>(R.id.category_plugins)
        for (plugin in plugins) {
            val pluginViewHolder = PluginViewHolder(this, pluginType, plugin)
            pluginContainer.addView(pluginViewHolder.baseView)
            pluginViewHolders.add(pluginViewHolder)
        }
        configbuilder_categories.addView(parent)
    }

}
