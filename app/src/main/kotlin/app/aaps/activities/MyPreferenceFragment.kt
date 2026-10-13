package app.aaps.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.widget.TextView
import androidx.annotation.XmlRes
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.size
import app.aaps.R
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.overview.Overview
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.ProtectionCheck.ProtectionType.BIOMETRIC
import app.aaps.core.interfaces.protection.ProtectionCheck.ProtectionType.CUSTOM_PASSWORD
import app.aaps.core.interfaces.protection.ProtectionCheck.ProtectionType.CUSTOM_PIN
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventRebuildTabs
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.utils.extensions.safeGetSerializable
import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.EditTextValidator
import app.aaps.core.validators.preferences.AdaptiveClickPreference
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveListIntPreference
import app.aaps.core.validators.preferences.AdaptiveListPreference
import app.aaps.core.validators.preferences.AdaptiveStringPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.aps.autotune.AutotunePlugin
import app.aaps.plugins.automation.AutomationPlugin
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import app.aaps.plugins.main.general.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.main.skins.SkinProvider
import dagger.android.support.AndroidSupportInjection
import java.util.Vector
import javax.inject.Inject

class MyPreferenceFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {

    private var pluginName: String? = null
    private var customPreference: UiInteraction.Preferences? = null
    private var filter = ""

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var config: Config
    @Inject lateinit var passwordCheck: PasswordCheck
    @Inject lateinit var automationPlugin: AutomationPlugin
    @Inject lateinit var autotunePlugin: AutotunePlugin
    @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    @Inject lateinit var maintenancePlugin: MaintenancePlugin
    @Inject lateinit var skinProvider: SkinProvider
    @Inject lateinit var overview: Overview

    companion object {

        const val FILTER = "filter"
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun setArguments(args: Bundle?) {
        super.setArguments(args)
        pluginName = args?.getString(UiInteraction.PLUGIN_NAME)
        customPreference = args?.safeGetSerializable(UiInteraction.PREFERENCE, UiInteraction.Preferences::class.java)
        filter = args?.getString("filter") ?: ""
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(UiInteraction.PLUGIN_NAME, pluginName)
        customPreference?.let { outState.putSerializable(UiInteraction.PREFERENCE, it) }
        outState.putString("filter", filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        context?.let { context ->
            PreferenceManager
                .getDefaultSharedPreferences(context)
                .unregisterOnSharedPreferenceChangeListener(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context?.let { context ->
            PreferenceManager
                .getDefaultSharedPreferences(context)
                .registerOnSharedPreferenceChangeListener(this)
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<TextView>(R.id.version)?.let { overview.setVersionView(it) }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        (savedInstanceState ?: arguments)?.let { bundle ->
            pluginName = bundle.getString(UiInteraction.PLUGIN_NAME)
            customPreference = bundle.safeGetSerializable(UiInteraction.PREFERENCE, UiInteraction.Preferences::class.java)
            filter = bundle.getString(FILTER, "")
        }
        if (pluginName != null) {
            val plugin = activePlugin.getPluginsList().firstOrNull { it.javaClass.simpleName == pluginName } ?: error("Plugin not found")
            addPreferencesIfEnabled(plugin, rootKey)
        } else if (customPreference == UiInteraction.Preferences.PROTECTION) {
            addProtectionScreen(rootKey)
        } else {
            addGeneralScreen(rootKey)
            addProtectionScreen(rootKey)
            addPreferencesIfEnabled(activePlugin.activeOverview as PluginBase, rootKey)
            addPreferencesIfEnabled(activePlugin.activeSafety as PluginBase, rootKey)
            addPreferencesIfEnabled(activePlugin.activeBgSource as PluginBase, rootKey)
            activePlugin.getSpecificPluginsList(PluginType.LOOP).forEach { addPreferencesIfEnabled(it, rootKey, config.APS) }
            addPreferencesIfEnabled(activePlugin.activeAPS as PluginBase, rootKey, config.APS)
            addPreferencesIfEnabled(activePlugin.activeSensitivity as PluginBase, rootKey)
            addPreferencesIfEnabled(activePlugin.activePump as PluginBase, rootKey)
            addPumpScreen(rootKey)
            addPreferencesIfEnabled(activePlugin.activeInsulin as PluginBase, rootKey)
            activePlugin.getSpecificPluginsList(PluginType.SYNC).forEach { addPreferencesIfEnabled(it, rootKey) }
            addPreferencesIfEnabled(smsCommunicatorPlugin, rootKey)
            addPreferencesIfEnabled(automationPlugin, rootKey)
            addPreferencesIfEnabled(autotunePlugin, rootKey)
            addAlertScreen(rootKey)
            addPreferencesIfEnabled(maintenancePlugin, rootKey)
        }
        try {
            initSummary(preferenceScreen, pluginName != null)
        } catch (e: Exception) {
            throw Exception("Error in onCreatePreferences pluginName=$pluginName customPreference=$customPreference rootKey=$rootKey filter=$filter", e)
        }
        preprocessPreferences()
        if (filter != "") updateFilterVisibility(filter, preferenceScreen)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        key ?: return
        rxBus.send(EventPreferenceChange(key))
        if (key == StringKey.GeneralLanguage.key) {
            rxBus.send(EventRebuildTabs(true))
            //recreate() does not update language so better close settings
            activity?.finish()
        }
        if (key == BooleanKey.OverviewShortTabTitles.key || key == BooleanKey.GeneralSimpleMode.key) {
            rxBus.send(EventRebuildTabs())
        }
        if (key == StringKey.GeneralUnits.key || key == BooleanKey.GeneralSimpleMode.key || preferences.getDependingOn(key).isNotEmpty()) {
            activity?.recreate()
            return
        }
        if (key == BooleanKey.ApsUseAutosens.key && preferences.get(BooleanKey.ApsUseAutosens)) {
            activity?.let {
                OKDialog.show(it, rh.gs(app.aaps.plugins.configuration.R.string.configbuilder_sensitivity), rh.gs(R.string.sensitivity_warning))
            }
        }
        // Preference change can be triggered inside AAPS on [NonPreferenceKey] too
        // check if it's [PreferenceKey]
        if (preferences.get(key) is PreferenceKey?)
            checkForBiometricFallback(preferences.get(key) as PreferenceKey?)

        preprocessCustomVisibility(preferenceScreen)
        updatePrefSummary(findPreference(key))
        preprocessPreferences()
    }

    // Update preferences with calculated visibility
    private fun preprocessCustomVisibility(p: Preference?) {
        if (p is AdaptiveClickPreference) p.calculatedVisibility?.let { p.isVisible = it.invoke() }
        if (p is PreferenceGroup) for (i in 0 until p.preferenceCount) preprocessCustomVisibility(p.getPreference(i))
    }

    private fun preprocessPreferences() {
        // Do plugin overrides
        for (plugin in activePlugin.getPluginsList()) {
            if (plugin.isEnabled()) plugin.preprocessPreferences(this)
        }
    }

    private fun checkForBiometricFallback(key: PreferenceKey?) {
        // Biometric protection activated without set master password
        if ((IntKey.ProtectionTypeSettings == key || IntKey.ProtectionTypeApplication == key || IntKey.ProtectionTypeBolus == key) &&
            preferences.get(StringKey.ProtectionMasterPassword) == "" &&
            preferences.get(key as IntKey) == BIOMETRIC.ordinal
        ) {
            activity?.let {
                val title = rh.gs(app.aaps.core.ui.R.string.unsecure_fallback_biometric)
                val message = rh.gs(app.aaps.plugins.configuration.R.string.master_password_missing, rh.gs(app.aaps.plugins.configuration.R.string.protection))
                OKDialog.show(it, title = title, message = message)
            }
        }

        // Master password erased with activated Biometric protection
        val isBiometricActivated = preferences.get(IntKey.ProtectionTypeSettings) == BIOMETRIC.ordinal ||
            preferences.get(IntKey.ProtectionTypeApplication) == BIOMETRIC.ordinal ||
            preferences.get(IntKey.ProtectionTypeBolus) == BIOMETRIC.ordinal
        if (StringKey.ProtectionMasterPassword == key && preferences.get(key as StringKey) == "" && isBiometricActivated) {
            activity?.let {
                val title = rh.gs(app.aaps.core.ui.R.string.unsecure_fallback_biometric)
                val message = rh.gs(app.aaps.core.ui.R.string.unsecure_fallback_descriotion_biometric)
                OKDialog.show(it, title = title, message = message)
            }
        }
    }

    private fun addPreferencesIfEnabled(p: PluginBase, rootKey: String?, enabled: Boolean = true) {
        if (preferences.simpleMode && !p.pluginDescription.preferencesVisibleInSimpleMode && !config.isDev()) return
        if (enabled && p.isEnabled() && p.preferencesId == PluginDescription.PREFERENCE_SCREEN)
            addPreferencesFromScreen(p, rootKey)
        if (enabled && p.isEnabled() && p.preferencesId > 0)
            addPreferencesFromResource(p.preferencesId, rootKey)
    }

    @SuppressLint("RestrictedApi")
    private fun addPreferencesFromResource(@XmlRes preferencesResId: Int, key: String?, enabled: Boolean = true) {
        if (enabled) {
            val xmlRoot = preferenceManager.inflateFromResource(requireContext(), preferencesResId, null)
            if (key != null) {
                // looking for sub screen
                val root: Preference = xmlRoot.findPreference(key) ?: return
                require(root is PreferenceScreen) { ("Preference object with key $key is not a PreferenceScreen") }
                preferenceScreen = root
            } else {
                addPreferencesFromResource(preferencesResId)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun addPreferencesFromScreen(p: PluginBase, key: String?) {
        val rootScreen = preferenceScreen ?: preferenceManager.createPreferenceScreen(requireContext()).also { preferenceScreen = it }
        p.addPreferenceScreen(preferenceManager, rootScreen, requireContext(), key)
        if (key != null) {
            // looking for sub screen
            val root: Preference = rootScreen.findPreference(key) ?: return
            require(root is PreferenceScreen) { ("Preference object with key $key is not a PreferenceScreen") }
            preferenceScreen = root
        }
    }

    private fun updateFilterVisibility(filter: String, p: Preference): Boolean {

        var visible = false

        if (p is PreferenceGroup) {
            for (i in 0 until p.preferenceCount)
                visible = updateFilterVisibility(filter, p.getPreference(i)) || visible
            if (visible && p is PreferenceCategory) p.initialExpandedChildrenCount = Int.MAX_VALUE
        } else {
            @Suppress("KotlinConstantConditions")
            visible = visible || p.key?.contains(filter, true) == true
            visible = visible || p.title?.contains(filter, true) == true
            visible = visible || p.summary?.contains(filter, true) == true
        }

        p.isVisible = visible
        return visible
    }

    private fun updatePrefSummary(pref: Preference?) {
        pref ?: return
        val keyDefinition = pref.key?.let { preferences.getIfExists(it) }
        when (keyDefinition) {
            is IntPreferenceKey,
            is DoublePreferenceKey -> {
                if (pref is EditTextPreference && pref.text != null) pref.summary = pref.text
                if (pref is ListPreference) pref.summary = pref.entry
            }

            is StringPreferenceKey -> {
                val value = preferences.get(keyDefinition)
                when {
                    // We use Preference and custom editor instead of EditTextPreference
                    // to hash password while it is saved and never have to show it, even hashed
                    (keyDefinition.isPin || keyDefinition.isPassword) && value.isNotEmpty() -> pref.summary = "******"
                    keyDefinition.isPin                                                     -> pref.summary = rh.gs(app.aaps.core.ui.R.string.pin_not_set)
                    keyDefinition.isPassword                                                -> pref.summary = rh.gs(app.aaps.core.ui.R.string.password_not_set)
                    pref is EditTextPreference && value.isNotEmpty()                        -> pref.summary = value
                    pref is ListPreference                                                  -> pref.summary = pref.entry
                }
            }

        }
        for (plugin in activePlugin.getPluginsList()) pref.key?.let { plugin.updatePreferenceSummary(pref) }
    }

    private fun initSummary(p: Preference?, isSinglePreference: Boolean) {
        p?.isIconSpaceReserved = false // remove extra spacing on left after migration to androidx
        // expand single plugin preference by default
        if (p is PreferenceScreen && isSinglePreference && p.size > 0 && p.getPreference(0) is PreferenceCategory)
            (p.getPreference(0) as PreferenceCategory).initialExpandedChildrenCount = Int.MAX_VALUE
        if (p is PreferenceGroup) {
            for (i in 0 until p.preferenceCount) {
                initSummary(p.getPreference(i), isSinglePreference)
            }
        } else {
            updatePrefSummary(p)
        }
    }

    fun setFilter(filter: String) {
        this.filter = filter
        preferenceManager?.preferenceScreen?.let { updateFilterVisibility(filter, it) }
    }

    private fun addGeneralScreen(rootKey: String?) {
        if (rootKey != null) return

        val context = requireContext()
        val rootScreen = preferenceScreen ?: preferenceManager.createPreferenceScreen(context).also { preferenceScreen = it }

        val unitsEntries = arrayOf<CharSequence>("mg/dL", "mmol/L")
        val unitsValues = arrayOf<CharSequence>("mg/dl", "mmol")
        val languageEntries = arrayOf<CharSequence>(
            rh.gs(R.string.default_lang),
            "English",
            "Afrikaans",
            "Bulgarian",
            "Czech",
            "German",
            "Danish",
            "French",
            "Dutch",
            "Spanish",
            "Greek",
            "Irish",
            "Italian",
            "Korean",
            "Lithuanian",
            "Norwegian",
            "Polish",
            "Portuguese",
            "Portuguese, Brazilian",
            "Romanian",
            "Russian",
            "Slovak",
            "Swedish",
            "Turkish",
            "Chinese, Traditional",
            "Chinese, Simplified",
        )
        val languageValues = arrayOf<CharSequence>("default", "en", "af", "bg", "cs", "de", "dk", "fr", "nl", "es", "el", "ga", "it", "ko", "lt", "nb", "pl", "pt", "pt_BR", "ro", "ru", "sk", "sv", "tr", "zh_TW", "zh_CN")
        assert(languageEntries.size == languageValues.size)

        val skinEntries = Vector<CharSequence>()
        val skinValues = Vector<CharSequence>()

        for (skin in skinProvider.list) {
            skinValues.addElement(skin.javaClass.name)
            skinEntries.addElement(context.getString(skin.description))
        }

        val darkModeEntries = arrayOf<CharSequence>(
            rh.gs(app.aaps.plugins.main.R.string.dark_theme),
            rh.gs(app.aaps.plugins.main.R.string.light_theme),
            rh.gs(app.aaps.plugins.main.R.string.follow_system_theme),
        )
        val darkModeValues = arrayOf<CharSequence>(
            rh.gs(app.aaps.plugins.main.R.string.value_dark_theme),
            rh.gs(app.aaps.plugins.main.R.string.value_light_theme),
            rh.gs(app.aaps.plugins.main.R.string.value_system_theme),
        )

        val category = PreferenceCategory(context)
        rootScreen.addPreference(category)
        category.apply {
            key = "general_settings"
            title = rh.gs(app.aaps.plugins.configuration.R.string.configbuilder_general)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveListPreference(ctx = context, stringKey = StringKey.GeneralUnits, title = R.string.unitsnosemicolon, entries = unitsEntries, entryValues = unitsValues))
            addPreference(AdaptiveListPreference(ctx = context, stringKey = StringKey.GeneralLanguage, title = R.string.language, entries = languageEntries, entryValues = languageValues))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.GeneralSimpleMode, title = R.string.simple_mode))
            addPreference(
                AdaptiveStringPreference(
                    ctx = context, stringKey = StringKey.GeneralPatientName, summary = app.aaps.plugins.configuration.R.string.patient_name_summary, title = app.aaps.plugins.configuration.R.string.patient_name,
                    validatorParams = DefaultEditTextValidator.Parameters(testType = EditTextValidator.TEST_PERSONNAME)
                )
            )
            addPreference(AdaptiveListPreference(ctx = context, stringKey = StringKey.GeneralSkin, title = app.aaps.plugins.main.R.string.skin, entries = skinEntries.toTypedArray(), entryValues = skinValues.toTypedArray()))
            addPreference(
                AdaptiveListPreference(
                    ctx = context,
                    stringKey = StringKey.GeneralDarkMode,
                    entries = darkModeEntries,
                    entryValues = darkModeValues,
                    title = app.aaps.plugins.main.R.string.app_color_scheme,
                    summary = app.aaps.plugins.main.R.string.theme_switcher_summary
                )
            )
        }
    }

    private fun addProtectionScreen(rootKey: String?) {
        if (rootKey != null) return

        val context = requireContext()
        val rootScreen = preferenceScreen ?: preferenceManager.createPreferenceScreen(context).also { preferenceScreen = it }

        val protectionTypeEntries = arrayOf<CharSequence>(
            rh.gs(app.aaps.core.ui.R.string.noprotection),
            rh.gs(app.aaps.core.ui.R.string.biometric),
            rh.gs(app.aaps.core.ui.R.string.master_password),
            rh.gs(app.aaps.core.ui.R.string.custom_password),
            rh.gs(app.aaps.core.ui.R.string.custom_pin),
        )
        val protectionTypeValues = arrayOf<CharSequence>("0", "1", "2", "3", "4")

        val category = PreferenceCategory(context)
        rootScreen.addPreference(category)
        category.apply {
            key = "protection_settings"
            title = rh.gs(app.aaps.plugins.configuration.R.string.protection)
            initialExpandedChildrenCount = 0
            addPreference(
                AdaptiveClickPreference(ctx = context, stringKey = StringKey.ProtectionMasterPassword, title = app.aaps.core.ui.R.string.master_password,
                                        onPreferenceClickListener = {
                                            passwordCheck.queryPassword(context, app.aaps.plugins.configuration.R.string.current_master_password, StringKey.ProtectionMasterPassword, {
                                                passwordCheck.setPassword(context, app.aaps.core.ui.R.string.master_password, StringKey.ProtectionMasterPassword)
                                            })
                                            true
                                        }
                )
            )
            addPreference(AdaptiveListIntPreference(ctx = context, intKey = IntKey.ProtectionTypeSettings, title = app.aaps.core.ui.R.string.settings_protection, entries = protectionTypeEntries, entryValues = protectionTypeValues))
            addPreference(
                AdaptiveClickPreference(ctx = context, stringKey = StringKey.ProtectionSettingsPassword, title = app.aaps.core.ui.R.string.settings_password,
                                        onPreferenceClickListener = {
                                            passwordCheck.setPassword(context, app.aaps.core.ui.R.string.settings_password, StringKey.ProtectionSettingsPassword)
                                            true
                                        },
                                        calculatedVisibility = { preferences.get(IntKey.ProtectionTypeSettings) == CUSTOM_PASSWORD.ordinal })
            )
            addPreference(
                AdaptiveClickPreference(ctx = context, stringKey = StringKey.ProtectionSettingsPin, title = app.aaps.core.ui.R.string.settings_pin,
                                        onPreferenceClickListener = {
                                            passwordCheck.setPassword(context, app.aaps.core.ui.R.string.settings_pin, StringKey.ProtectionSettingsPin, pinInput = true)
                                            true
                                        },
                                        calculatedVisibility = { preferences.get(IntKey.ProtectionTypeSettings) == CUSTOM_PIN.ordinal })
            )
            addPreference(AdaptiveListIntPreference(ctx = context, intKey = IntKey.ProtectionTypeApplication, title = app.aaps.core.ui.R.string.application_protection, entries = protectionTypeEntries, entryValues = protectionTypeValues))
            addPreference(
                AdaptiveClickPreference(ctx = context, stringKey = StringKey.ProtectionApplicationPassword, title = app.aaps.core.ui.R.string.application_password,
                                        onPreferenceClickListener = {
                                            passwordCheck.setPassword(context, app.aaps.core.ui.R.string.application_password, StringKey.ProtectionApplicationPassword)
                                            true
                                        },
                                        calculatedVisibility = { preferences.get(IntKey.ProtectionTypeApplication) == CUSTOM_PASSWORD.ordinal })
            )
            addPreference(
                AdaptiveClickPreference(ctx = context, stringKey = StringKey.ProtectionApplicationPin, title = app.aaps.core.ui.R.string.application_pin,
                                        onPreferenceClickListener = {
                                            passwordCheck.setPassword(context, app.aaps.core.ui.R.string.application_pin, StringKey.ProtectionApplicationPin, pinInput = true)
                                            true
                                        },
                                        calculatedVisibility = { preferences.get(IntKey.ProtectionTypeApplication) == CUSTOM_PIN.ordinal })
            )
            addPreference(AdaptiveListIntPreference(ctx = context, intKey = IntKey.ProtectionTypeBolus, title = app.aaps.core.ui.R.string.bolus_protection, entries = protectionTypeEntries, entryValues = protectionTypeValues))
            addPreference(
                AdaptiveClickPreference(ctx = context, stringKey = StringKey.ProtectionBolusPassword, title = app.aaps.core.ui.R.string.bolus_password,
                                        onPreferenceClickListener = {
                                            passwordCheck.setPassword(context, app.aaps.core.ui.R.string.bolus_password, StringKey.ProtectionBolusPassword)
                                            true
                                        },
                                        calculatedVisibility = { preferences.get(IntKey.ProtectionTypeBolus) == CUSTOM_PASSWORD.ordinal })
            )
            addPreference(
                AdaptiveClickPreference(ctx = context, stringKey = StringKey.ProtectionBolusPin, title = app.aaps.core.ui.R.string.bolus_pin,
                                        onPreferenceClickListener = {
                                            passwordCheck.setPassword(context, app.aaps.core.ui.R.string.bolus_pin, StringKey.ProtectionBolusPin, pinInput = true)
                                            true
                                        },
                                        calculatedVisibility = { preferences.get(IntKey.ProtectionTypeBolus) == CUSTOM_PIN.ordinal })
            )
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.ProtectionTimeout, title = app.aaps.core.ui.R.string.protection_timeout_title, summary = app.aaps.core.ui.R.string.protection_timeout_summary))
        }
    }

    private fun addPumpScreen(rootKey: String?) {
        if (rootKey != null) return

        val context = requireContext()
        val rootScreen = preferenceScreen ?: preferenceManager.createPreferenceScreen(context).also { preferenceScreen = it }

        val category = PreferenceCategory(context)
        rootScreen.addPreference(category)
        category.apply {
            key = "pump_settings"
            title = rh.gs(app.aaps.core.ui.R.string.pump)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.PumpBtWatchdog, title = app.aaps.core.ui.R.string.btwatchdog_title, summary = app.aaps.core.ui.R.string.btwatchdog_summary))
        }
    }

    private fun addAlertScreen(rootKey: String?) {
        if (rootKey != null) return

        val context = requireContext()
        val rootScreen = preferenceScreen ?: preferenceManager.createPreferenceScreen(context).also { preferenceScreen = it }

        val category = PreferenceCategory(context)
        rootScreen.addPreference(category)
        category.apply {
            key = "local_alerts_settings"
            title = rh.gs(R.string.localalertsettings_title)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.AlertMissedBgReading, title = R.string.enable_missed_bg_readings_alert))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.AlertsStaleDataThreshold, title = app.aaps.plugins.sync.R.string.ns_alarm_stale_data_value_label))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.AlertPumpUnreachable, title = R.string.enable_pump_unreachable_alert))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.AlertsPumpUnreachableThreshold, title = R.string.pump_unreachable_threshold))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.AlertCarbsRequired, title = R.string.enable_carbs_req_alert))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.AlertUrgentAsAndroidNotification, title = app.aaps.core.ui.R.string.raise_notifications_as_android_notifications))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.AlertIncreaseVolume, title = R.string.gradually_increase_notification_volume))
        }
    }
}
