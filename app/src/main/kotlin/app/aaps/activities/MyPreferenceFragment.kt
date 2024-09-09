package app.aaps.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
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
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.ProtectionCheck.ProtectionType.BIOMETRIC
import app.aaps.core.interfaces.protection.ProtectionCheck.ProtectionType.CUSTOM_PASSWORD
import app.aaps.core.interfaces.protection.ProtectionCheck.ProtectionType.CUSTOM_PIN
import app.aaps.core.interfaces.protection.ProtectionCheck.ProtectionType.NONE
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventRebuildTabs
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.AdaptiveClickPreference
import app.aaps.core.keys.AdaptiveListIntPreference
import app.aaps.core.keys.AdaptiveListPreference
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.validators.AdaptiveIntPreference
import app.aaps.core.validators.AdaptiveStringPreference
import app.aaps.core.validators.AdaptiveSwitchPreference
import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.EditTextValidator
import app.aaps.plugins.aps.autotune.AutotunePlugin
import app.aaps.plugins.automation.AutomationPlugin
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import app.aaps.plugins.main.general.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.main.skins.SkinProvider
import dagger.android.support.AndroidSupportInjection
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Vector
import javax.inject.Inject

class MyPreferenceFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {

    private var pluginName: String? = null
    private var customPreference: UiInteraction.Preferences? = null
    private var filter = ""

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var config: Config
    @Inject lateinit var passwordCheck: PasswordCheck
    @Inject lateinit var nsSettingStatus: NSSettingsStatus

    @Inject lateinit var automationPlugin: AutomationPlugin
    @Inject lateinit var autotunePlugin: AutotunePlugin
    @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    @Inject lateinit var maintenancePlugin: MaintenancePlugin
    @Inject lateinit var skinProvider: SkinProvider

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
        @Suppress("DEPRECATION")
        customPreference = args?.getSerializable(UiInteraction.PREFERENCE) as UiInteraction.Preferences?
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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        (savedInstanceState ?: arguments)?.let { bundle ->
            pluginName = bundle.getString(UiInteraction.PLUGIN_NAME)
            @Suppress("DEPRECATION")
            customPreference = bundle.getSerializable(UiInteraction.PREFERENCE) as UiInteraction.Preferences?
            filter = bundle.getString(FILTER, "")
        }
        if (pluginName != null) {
            val plugin = activePlugin.getPluginsList().firstOrNull { it.javaClass.simpleName == pluginName } ?: error("Plugin not found")
            addPreferencesIfEnabled(plugin, rootKey)
        } else if (customPreference != null) {
            when (customPreference!!) {
                UiInteraction.Preferences.PROTECTION -> addProtectionScreen()
            }
        } else {
            addGeneralScreen()
            addProtectionScreen()
            addPreferencesIfEnabled(activePlugin.activeOverview as PluginBase, rootKey)
            addPreferencesIfEnabled(activePlugin.activeSafety as PluginBase, rootKey)
            addPreferencesIfEnabled(activePlugin.activeBgSource as PluginBase, rootKey)
            activePlugin.getSpecificPluginsList(PluginType.LOOP).forEach { addPreferencesIfEnabled(it, rootKey, config.APS) }
            addPreferencesIfEnabled(activePlugin.activeAPS as PluginBase, rootKey, config.APS)
            addPreferencesIfEnabled(activePlugin.activeSensitivity as PluginBase, rootKey)
            addPreferencesIfEnabled(activePlugin.activePump as PluginBase, rootKey)
            addPumpScreen()
            addPreferencesIfEnabled(activePlugin.activeInsulin as PluginBase, rootKey)
            activePlugin.getSpecificPluginsList(PluginType.SYNC).forEach { addPreferencesIfEnabled(it, rootKey) }
            addPreferencesIfEnabled(smsCommunicatorPlugin, rootKey)
            addPreferencesIfEnabled(automationPlugin, rootKey)
            addPreferencesIfEnabled(autotunePlugin, rootKey)
            addPreferencesFromResource(R.xml.pref_alerts, rootKey)
            addPreferencesIfEnabled(maintenancePlugin, rootKey)
        }
        initSummary(preferenceScreen, pluginName != null)
        preprocessPreferences()
        if (filter != "") updateFilterVisibility(filter, preferenceScreen)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        key ?: return
        rxBus.send(EventPreferenceChange(key))
        if (key == rh.gs(StringKey.GeneralLanguage.key)) {
            rxBus.send(EventRebuildTabs(true))
            //recreate() does not update language so better close settings
            activity?.finish()
        }
        if (key == rh.gs(BooleanKey.OverviewShortTabTitles.key) || key == rh.gs(BooleanKey.GeneralSimpleMode.key)) {
            rxBus.send(EventRebuildTabs())
        }
        if (key == rh.gs(StringKey.GeneralUnits.key) || key == rh.gs(BooleanKey.GeneralSimpleMode.key) || preferences.getDependingOn(key).isNotEmpty()) {
            activity?.recreate()
            return
        }
        if (key == rh.gs(BooleanKey.ApsUseAutosens.key) && preferences.get(BooleanKey.ApsUseAutosens)) {
            activity?.let {
                OKDialog.show(it, rh.gs(app.aaps.plugins.configuration.R.string.configbuilder_sensitivity), rh.gs(R.string.sensitivity_warning))
            }
        }
        checkForBiometricFallback(key)

        updatePrefSummary(findPreference(key))
        preprocessPreferences()
    }

    private fun preprocessPreferences() {
        // Do plugin overrides
        for (plugin in activePlugin.getPluginsList()) {
            if (plugin.isEnabled()) plugin.preprocessPreferences(this)
        }
    }

    private fun checkForBiometricFallback(key: String) {
        // Biometric protection activated without set master password
        if ((rh.gs(app.aaps.core.keys.R.string.key_settings_protection) == key ||
                rh.gs(app.aaps.core.keys.R.string.key_application_protection) == key ||
                rh.gs(app.aaps.core.keys.R.string.key_bolus_protection) == key) &&
            sp.getString(app.aaps.core.keys.R.string.key_master_password, "") == "" &&
            sp.getInt(key, NONE.ordinal) == BIOMETRIC.ordinal
        ) {
            activity?.let {
                val title = rh.gs(app.aaps.core.ui.R.string.unsecure_fallback_biometric)
                val message = rh.gs(app.aaps.plugins.configuration.R.string.master_password_missing, rh.gs(app.aaps.plugins.configuration.R.string.protection))
                OKDialog.show(it, title = title, message = message)
            }
        }

        // Master password erased with activated Biometric protection
        val isBiometricActivated = sp.getInt(app.aaps.core.keys.R.string.key_settings_protection, NONE.ordinal) == BIOMETRIC.ordinal ||
            sp.getInt(app.aaps.core.keys.R.string.key_application_protection, NONE.ordinal) == BIOMETRIC.ordinal ||
            sp.getInt(app.aaps.core.keys.R.string.key_bolus_protection, NONE.ordinal) == BIOMETRIC.ordinal
        if (rh.gs(app.aaps.core.keys.R.string.key_master_password) == key && sp.getString(key, "") == "" && isBiometricActivated) {
            activity?.let {
                val title = rh.gs(app.aaps.core.ui.R.string.unsecure_fallback_biometric)
                val message = rh.gs(app.aaps.core.ui.R.string.unsecure_fallback_descriotion_biometric)
                OKDialog.show(it, title = title, message = message)
            }
        }
    }

    private fun addPreferencesIfEnabled(p: PluginBase, rootKey: String?, enabled: Boolean = true) {
        if (preferences.simpleMode && !p.pluginDescription.preferencesVisibleInSimpleMode) return
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
        if (pref is ListPreference) {
            pref.setSummary(pref.entry)
            // Preferences
            if (pref.getKey() == rh.gs(app.aaps.core.keys.R.string.key_settings_protection)) {
                val pass: Preference? = findPreference(rh.gs(app.aaps.core.keys.R.string.key_settings_password))
                val usePassword = pref.value == CUSTOM_PASSWORD.ordinal.toString()
                pass?.let { it.isVisible = usePassword }
                val pin: Preference? = findPreference(rh.gs(app.aaps.core.keys.R.string.key_settings_pin))
                val usePin = pref.value == CUSTOM_PIN.ordinal.toString()
                pin?.let { it.isVisible = usePin }
            }
            // Application
            if (pref.getKey() == rh.gs(app.aaps.core.keys.R.string.key_application_protection)) {
                val pass: Preference? = findPreference(rh.gs(app.aaps.core.keys.R.string.key_application_password))
                val usePassword = pref.value == CUSTOM_PASSWORD.ordinal.toString()
                pass?.let { it.isVisible = usePassword }
                val pin: Preference? = findPreference(rh.gs(app.aaps.core.keys.R.string.key_application_pin))
                val usePin = pref.value == CUSTOM_PIN.ordinal.toString()
                pin?.let { it.isVisible = usePin }
            }
            // Bolus
            if (pref.getKey() == rh.gs(app.aaps.core.keys.R.string.key_bolus_protection)) {
                val pass: Preference? = findPreference(rh.gs(app.aaps.core.keys.R.string.key_bolus_password))
                val usePassword = pref.value == CUSTOM_PASSWORD.ordinal.toString()
                pass?.let { it.isVisible = usePassword }
                val pin: Preference? = findPreference(rh.gs(app.aaps.core.keys.R.string.key_bolus_pin))
                val usePin = pref.value == CUSTOM_PIN.ordinal.toString()
                pin?.let { it.isVisible = usePin }
            }
        }
        if (pref is EditTextPreference) {
            if (pref.getKey().contains("password") || pref.getKey().contains("pin") || pref.getKey().contains("secret") || pref.getKey().contains("token")) {
                pref.setSummary("******")
            } else if (pref.text != null) {
                pref.setSummary(pref.text)
            }
        }

        for (plugin in activePlugin.getPluginsList()) {
            pref?.let { it.key?.let { plugin.updatePreferenceSummary(pref) } }
        }

        val hmacPasswords = arrayOf(
            rh.gs(app.aaps.core.keys.R.string.key_bolus_password),
            rh.gs(app.aaps.core.keys.R.string.key_master_password),
            rh.gs(app.aaps.core.keys.R.string.key_application_password),
            rh.gs(app.aaps.core.keys.R.string.key_settings_password),
            rh.gs(app.aaps.core.keys.R.string.key_bolus_pin),
            rh.gs(app.aaps.core.keys.R.string.key_application_pin),
            rh.gs(app.aaps.core.keys.R.string.key_settings_pin)
        )

        if (pref is Preference && (pref.key != null) && (hmacPasswords.contains(pref.key))) {
            if (sp.getString(pref.key, "").startsWith("hmac:")) {
                pref.summary = "******"
            } else {
                if (pref.key.contains("pin")) {
                    pref.summary = rh.gs(app.aaps.core.ui.R.string.pin_not_set)
                } else {
                    pref.summary = rh.gs(app.aaps.core.ui.R.string.password_not_set)
                }
            }
        }
        pref?.let { adjustUnitDependentPrefs(it) }
    }

    private fun adjustUnitDependentPrefs(pref: Preference) { // convert preferences values to current units
        if (pref.key != null && preferences.isUnitDependent(pref.key) && pref is EditTextPreference) {
            val value = profileUtil.valueInCurrentUnitsDetect(SafeParse.stringToDouble(pref.text)).toString()
            val precision = if (profileUtil.units == GlucoseUnit.MGDL) 0 else 1
            val converted = BigDecimal(value).setScale(precision, RoundingMode.HALF_UP)
            pref.summary = converted.toPlainString()
        }
    }

    private fun initSummary(p: Preference, isSinglePreference: Boolean) {
        p.isIconSpaceReserved = false // remove extra spacing on left after migration to androidx
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

    // We use Preference and custom editor instead of EditTextPreference
    // to hash password while it is saved and never have to show it, even hashed

    override fun onPreferenceTreeClick(preference: Preference): Boolean =
        when (preference.key) {
            // rh.gs(app.aaps.core.keys.R.string.key_master_password)      -> {
            //     passwordCheck.queryPassword(requireContext(), app.aaps.plugins.configuration.R.string.current_master_password, app.aaps.core.keys.R.string.key_master_password, {
            //         passwordCheck.setPassword(requireContext(), app.aaps.core.ui.R.string.master_password, app.aaps.core.keys.R.string.key_master_password)
            //     })
            //     true
            // }

            // rh.gs(app.aaps.core.keys.R.string.key_settings_password) -> {
            //     passwordCheck.setPassword(requireContext(), app.aaps.core.ui.R.string.settings_password, app.aaps.core.keys.R.string.key_settings_password)
            //     true
            // }

            // rh.gs(app.aaps.core.keys.R.string.key_bolus_password) -> {
            //     passwordCheck.setPassword(requireContext(), app.aaps.core.ui.R.string.bolus_password, app.aaps.core.keys.R.string.key_bolus_password)
            //     true
            // }

            // rh.gs(app.aaps.core.keys.R.string.key_application_password) -> {
            //     passwordCheck.setPassword(requireContext(), app.aaps.core.ui.R.string.application_password, app.aaps.core.keys.R.string.key_application_password)
            //     true
            // }

            // rh.gs(app.aaps.core.keys.R.string.key_settings_pin) -> {
            //     passwordCheck.setPassword(requireContext(), app.aaps.core.ui.R.string.settings_pin, app.aaps.core.keys.R.string.key_settings_pin, pinInput = true)
            //     true
            // }

            // rh.gs(app.aaps.core.keys.R.string.key_bolus_pin) -> {
            //     passwordCheck.setPassword(requireContext(), app.aaps.core.ui.R.string.bolus_pin, app.aaps.core.keys.R.string.key_bolus_pin, pinInput = true)
            //     true
            // }

            // rh.gs(app.aaps.core.keys.R.string.key_application_pin) -> {
            //     passwordCheck.setPassword(requireContext(), app.aaps.core.ui.R.string.application_pin, app.aaps.core.keys.R.string.key_application_pin, pinInput = true)
            //     true
            // }
            // NSClient copy settings
            rh.gs(app.aaps.core.keys.R.string.key_statuslights_copy_ns) -> {
                nsSettingStatus.copyStatusLightsNsSettings(context)
                true
            }

            else                                                        -> super.onPreferenceTreeClick(preference)
        }

    fun setFilter(filter: String) {
        this.filter = filter
        preferenceManager?.preferenceScreen?.let { updateFilterVisibility(filter, it) }
    }

    private fun addGeneralScreen() {

        val rootScreen = preferenceScreen ?: preferenceManager.createPreferenceScreen(requireContext()).also { preferenceScreen = it }
        val context = requireContext()

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
            "Chinese",
            "Chinese Simplified",
        )
        val languageValues = arrayOf<CharSequence>("default", "en", "af", "bg", "cs", "de", "dk", "fr", "nl", "es", "el", "ga", "it", "ko", "lt", "nb", "pl", "pt", "pt_BR", "ro", "ru", "sk", "sv", "tr", "zh", "zh-CN")
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

    private fun addProtectionScreen() {

        val rootScreen = preferenceScreen ?: preferenceManager.createPreferenceScreen(requireContext()).also { preferenceScreen = it }
        val context = requireContext()

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
                                            passwordCheck.queryPassword(requireContext(), app.aaps.plugins.configuration.R.string.current_master_password, app.aaps.core.keys.R.string.key_master_password, {
                                                passwordCheck.setPassword(requireContext(), app.aaps.core.ui.R.string.master_password, app.aaps.core.keys.R.string.key_master_password)
                                            })
                                            true
                                        }
                )
            )
            addPreference(AdaptiveListIntPreference(ctx = context, intKey = IntKey.ProtectionTypeSettings, title = app.aaps.core.ui.R.string.settings_protection, entries = protectionTypeEntries, entryValues = protectionTypeValues))
            addPreference(
                AdaptiveClickPreference(ctx = context, stringKey = StringKey.ProtectionSettingsPassword, title = app.aaps.core.ui.R.string.settings_password,
                                         onPreferenceClickListener = {
                                             passwordCheck.setPassword(requireContext(), app.aaps.core.ui.R.string.settings_password, app.aaps.core.keys.R.string.key_settings_password)
                                             true
                                         })
            )
            addPreference(
                AdaptiveClickPreference(ctx = context, stringKey = StringKey.ProtectionSettingsPin, title = app.aaps.core.ui.R.string.settings_pin,
                                         onPreferenceClickListener = {
                                             passwordCheck.setPassword(requireContext(), app.aaps.core.ui.R.string.settings_pin, app.aaps.core.keys.R.string.key_settings_pin, pinInput = true)
                                             true
                                         })
            )
            addPreference(AdaptiveListIntPreference(ctx = context, intKey = IntKey.ProtectionTypeApplication, title = app.aaps.core.ui.R.string.application_protection, entries = protectionTypeEntries, entryValues = protectionTypeValues))
            addPreference(
                AdaptiveClickPreference(ctx = context, stringKey = StringKey.ProtectionApplicationPassword, title = app.aaps.core.ui.R.string.application_password,
                                         onPreferenceClickListener = {
                                             passwordCheck.setPassword(requireContext(), app.aaps.core.ui.R.string.application_password, app.aaps.core.keys.R.string.key_application_password)
                                             true
                                         })
            )
            addPreference(
                AdaptiveClickPreference(ctx = context, stringKey = StringKey.ProtectionApplicationPin, title = app.aaps.core.ui.R.string.application_pin,
                                         onPreferenceClickListener = {
                                             passwordCheck.setPassword(requireContext(), app.aaps.core.ui.R.string.application_pin, app.aaps.core.keys.R.string.key_application_pin, pinInput = true)
                                             true
                                         })
            )
            addPreference(AdaptiveListIntPreference(ctx = context, intKey = IntKey.ProtectionTypeBolus, title = app.aaps.core.ui.R.string.bolus_protection, entries = protectionTypeEntries, entryValues = protectionTypeValues))
            addPreference(
                AdaptiveClickPreference(ctx = context, stringKey = StringKey.ProtectionBolusPassword, title = app.aaps.core.ui.R.string.bolus_password,
                                         onPreferenceClickListener = {
                                             passwordCheck.setPassword(requireContext(), app.aaps.core.ui.R.string.bolus_password, app.aaps.core.keys.R.string.key_bolus_password)
                                             true
                                         })
            )
            addPreference(
                AdaptiveClickPreference(ctx = context, stringKey = StringKey.ProtectionBolusPin, title = app.aaps.core.ui.R.string.bolus_pin,
                                         onPreferenceClickListener = {
                                             passwordCheck.setPassword(requireContext(), app.aaps.core.ui.R.string.bolus_pin, app.aaps.core.keys.R.string.key_bolus_pin, pinInput = true)
                                             true
                                         })
            )
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.ProtectionTimeout, title = app.aaps.core.ui.R.string.protection_timeout_title, summary = app.aaps.core.ui.R.string.protection_timeout_summary))
        }
    }

    private fun addPumpScreen() {

        val rootScreen = preferenceScreen ?: preferenceManager.createPreferenceScreen(requireContext()).also { preferenceScreen = it }
        val context = requireContext()

        val category = PreferenceCategory(context)
        rootScreen.addPreference(category)
        category.apply {
            key = "pump_settings"
            title = rh.gs(app.aaps.core.ui.R.string.pump)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.PumpBtWatchdog, title = app.aaps.core.ui.R.string.btwatchdog_title, summary = app.aaps.core.ui.R.string.btwatchdog_summary))
        }
    }
}
