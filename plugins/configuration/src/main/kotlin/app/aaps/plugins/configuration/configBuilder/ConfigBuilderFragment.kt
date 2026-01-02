package app.aaps.plugins.configuration.configBuilder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionCheck.Protection.PREFERENCES
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.configBuilder.events.EventConfigBuilderUpdateGui
import app.aaps.plugins.configuration.databinding.ConfigbuilderFragmentBinding
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ConfigBuilderFragment : DaggerFragment() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var config: Config
    @Inject lateinit var uiInteraction: UiInteraction

    private var disposable: CompositeDisposable = CompositeDisposable()
    private val pluginViewHolders = ArrayList<ConfigBuilder.PluginViewHolderInterface>()
    private var inMenu = false
    private var queryingProtection = false
    private var _binding: ConfigbuilderFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ConfigbuilderFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val parentClass = this.activity?.let { it::class.java }
        inMenu = parentClass == uiInteraction.singleFragmentActivity
        updateProtectedUi()
        binding.unlock.setOnClickListener { queryProtection() }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        if (inMenu) queryProtection() else updateProtectedUi()
        disposable += rxBus
            .toObservable(EventConfigBuilderUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           for (pluginViewHolder in pluginViewHolders) pluginViewHolder.update(this.requireActivity())
                       }, fabricPrivacy::logException)
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Synchronized
    private fun updateGUI() {
        binding.categories.removeAllViews()
        configBuilder.createViewsForPlugins(
            title = R.string.configbuilder_profile,
            description = R.string.configbuilder_profile_description,
            pluginType = PluginType.PROFILE,
            plugins = activePlugin.getSpecificPluginsVisibleInList(PluginType.PROFILE),
            pluginViewHolders = pluginViewHolders,
            activity = requireActivity(),
            parent = binding.categories
        )
        if (config.APS || config.PUMPCONTROL || config.isEngineeringMode())
            configBuilder.createViewsForPlugins(
                title = app.aaps.core.ui.R.string.configbuilder_insulin,
                description = R.string.configbuilder_insulin_description,
                pluginType = PluginType.INSULIN,
                plugins = activePlugin.getSpecificPluginsVisibleInList(PluginType.INSULIN),
                pluginViewHolders = pluginViewHolders,
                activity = requireActivity(),
                parent = binding.categories
            )
        if (!config.AAPSCLIENT) {
            configBuilder.createViewsForPlugins(
                title = R.string.configbuilder_bgsource,
                description = R.string.configbuilder_bgsource_description,
                pluginType = PluginType.BGSOURCE,
                plugins = activePlugin.getSpecificPluginsVisibleInList(PluginType.BGSOURCE),
                pluginViewHolders = pluginViewHolders,
                activity = requireActivity(),
                parent = binding.categories
            )
            configBuilder.createViewsForPlugins(
                title = R.string.configbuilder_smoothing,
                description = R.string.configbuilder_smoothing_description,
                pluginType = PluginType.SMOOTHING,
                plugins = activePlugin.getSpecificPluginsVisibleInList(PluginType.SMOOTHING),
                pluginViewHolders = pluginViewHolders,
                activity = requireActivity(),
                parent = binding.categories
            )
            configBuilder.createViewsForPlugins(
                title = R.string.configbuilder_pump,
                description = R.string.configbuilder_pump_description,
                pluginType = PluginType.PUMP,
                plugins = activePlugin.getSpecificPluginsVisibleInList(PluginType.PUMP),
                pluginViewHolders = pluginViewHolders,
                activity = requireActivity(),
                parent = binding.categories
            )
        }
        if (config.APS || config.PUMPCONTROL || config.isEngineeringMode())
            configBuilder.createViewsForPlugins(
                title = R.string.configbuilder_sensitivity,
                description = R.string.configbuilder_sensitivity_description,
                pluginType = PluginType.SENSITIVITY,
                plugins = activePlugin.getSpecificPluginsVisibleInList(PluginType.SENSITIVITY),
                pluginViewHolders = pluginViewHolders,
                activity = requireActivity(),
                parent = binding.categories
            )
        if (config.APS) {
            configBuilder.createViewsForPlugins(
                title = R.string.configbuilder_aps,
                description = R.string.configbuilder_aps_description,
                pluginType = PluginType.APS,
                plugins = activePlugin.getSpecificPluginsVisibleInList(PluginType.APS),
                pluginViewHolders = pluginViewHolders,
                activity = requireActivity(),
                parent = binding.categories
            )
            configBuilder.createViewsForPlugins(
                title = R.string.configbuilder_loop,
                description = R.string.configbuilder_loop_description,
                pluginType = PluginType.LOOP,
                plugins = activePlugin.getSpecificPluginsVisibleInList(PluginType.LOOP),
                pluginViewHolders = pluginViewHolders,
                activity = requireActivity(),
                parent = binding.categories
            )
            configBuilder.createViewsForPlugins(
                title = app.aaps.core.ui.R.string.constraints,
                description = R.string.configbuilder_constraints_description,
                pluginType = PluginType.CONSTRAINTS,
                plugins = activePlugin.getSpecificPluginsVisibleInList(PluginType.CONSTRAINTS),
                pluginViewHolders = pluginViewHolders,
                activity = requireActivity(),
                parent = binding.categories
            )
        }
        configBuilder.createViewsForPlugins(
            title = R.string.configbuilder_sync,
            description = R.string.configbuilder_sync_description,
            pluginType = PluginType.SYNC,
            plugins = activePlugin.getSpecificPluginsVisibleInList(PluginType.SYNC),
            pluginViewHolders = pluginViewHolders,
            activity = requireActivity(),
            parent = binding.categories
        )
        configBuilder.createViewsForPlugins(
            title = R.string.configbuilder_general,
            description = R.string.configbuilder_general_description,
            pluginType = PluginType.GENERAL,
            plugins = activePlugin.getSpecificPluginsVisibleInList(PluginType.GENERAL),
            pluginViewHolders = pluginViewHolders,
            activity = requireActivity(),
            parent = binding.categories
        )
    }

    private fun updateProtectedUi() {
        val isLocked = protectionCheck.isLocked(PREFERENCES)
        binding.mainLayout.visibility = isLocked.not().toVisibility()
        binding.unlock.visibility = isLocked.toVisibility()
    }

    private fun queryProtection() {
        val isLocked = protectionCheck.isLocked(PREFERENCES)
        if (isLocked && !queryingProtection) {
            activity?.let { activity ->
                queryingProtection = true
                val doUpdate = { activity.runOnUiThread { queryingProtection = false; if (_binding != null) updateProtectedUi() } }
                protectionCheck.queryProtection(activity, PREFERENCES, doUpdate, doUpdate, doUpdate)
            }
        }
    }
}
