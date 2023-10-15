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
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.plugin.PluginBase
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
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.autotune.AutotunePlugin
import app.aaps.plugins.aps.loop.LoopPlugin
import app.aaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import app.aaps.plugins.aps.openAPSSMBDynamicISF.OpenAPSSMBDynamicISFPlugin
import app.aaps.plugins.automation.AutomationPlugin
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import app.aaps.plugins.constraints.safety.SafetyPlugin
import app.aaps.plugins.insulin.InsulinOrefFreePeakPlugin
import app.aaps.plugins.main.general.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.sync.garmin.GarminPlugin
import app.aaps.plugins.sync.wear.WearPlugin
import app.aaps.plugins.sensitivity.SensitivityAAPSPlugin
import app.aaps.plugins.sensitivity.SensitivityOref1Plugin
import app.aaps.plugins.sensitivity.SensitivityWeightedAveragePlugin
import app.aaps.plugins.source.AidexPlugin
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.EversensePlugin
import app.aaps.plugins.source.GlimpPlugin
import app.aaps.plugins.source.GlunovoPlugin
import app.aaps.plugins.source.IntelligoPlugin
import app.aaps.plugins.source.PoctechPlugin
import app.aaps.plugins.source.TomatoPlugin
import app.aaps.plugins.sync.nsclient.NSClientPlugin
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.openhumans.OpenHumansUploaderPlugin
import app.aaps.plugins.sync.tidepool.TidepoolPlugin
import app.aaps.plugins.sync.xdrip.XdripPlugin
import app.aaps.pump.virtual.VirtualPumpPlugin
import dagger.android.support.AndroidSupportInjection
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.plugins.pump.eopatch.EopatchPumpPlugin
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin
import info.nightscout.pump.combo.ComboPlugin
import info.nightscout.pump.combov2.ComboV2Plugin
import info.nightscout.pump.diaconn.DiaconnG8Plugin
import info.nightscout.pump.medtrum.MedtrumPlugin
import javax.inject.Inject

class MyPreferenceFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {

    private var pluginId = -1
    private var filter = ""

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var config: Config

    @Inject lateinit var automationPlugin: AutomationPlugin
    @Inject lateinit var autotunePlugin: AutotunePlugin
    @Inject lateinit var danaRPlugin: DanaRPlugin
    @Inject lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Inject lateinit var danaRv2Plugin: DanaRv2Plugin
    @Inject lateinit var danaRSPlugin: info.nightscout.pump.danars.DanaRSPlugin
    @Inject lateinit var comboPlugin: ComboPlugin
    @Inject lateinit var combov2Plugin: ComboV2Plugin
    @Inject lateinit var insulinOrefFreePeakPlugin: InsulinOrefFreePeakPlugin
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var localInsightPlugin: LocalInsightPlugin
    @Inject lateinit var medtronicPumpPlugin: MedtronicPumpPlugin
    @Inject lateinit var nsClientPlugin: NSClientPlugin
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var openAPSAMAPlugin: OpenAPSAMAPlugin
    @Inject lateinit var openAPSSMBPlugin: OpenAPSSMBPlugin
    @Inject lateinit var openAPSSMBDynamicISFPlugin: OpenAPSSMBDynamicISFPlugin
    @Inject lateinit var safetyPlugin: SafetyPlugin
    @Inject lateinit var sensitivityAAPSPlugin: SensitivityAAPSPlugin
    @Inject lateinit var sensitivityOref1Plugin: SensitivityOref1Plugin
    @Inject lateinit var sensitivityWeightedAveragePlugin: SensitivityWeightedAveragePlugin
    @Inject lateinit var dexcomPlugin: DexcomPlugin
    @Inject lateinit var eversensePlugin: EversensePlugin
    @Inject lateinit var glimpPlugin: GlimpPlugin
    @Inject lateinit var poctechPlugin: PoctechPlugin
    @Inject lateinit var tomatoPlugin: TomatoPlugin
    @Inject lateinit var glunovoPlugin: GlunovoPlugin
    @Inject lateinit var intelligoPlugin: IntelligoPlugin
    @Inject lateinit var aidexPlugin: AidexPlugin
    @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    @Inject lateinit var statusLinePlugin: XdripPlugin
    @Inject lateinit var tidepoolPlugin: TidepoolPlugin
    @Inject lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Inject lateinit var wearPlugin: WearPlugin
    @Inject lateinit var maintenancePlugin: MaintenancePlugin
    @Inject lateinit var eopatchPumpPlugin: EopatchPumpPlugin
    @Inject lateinit var medtrumPlugin: MedtrumPlugin

    @Inject lateinit var passwordCheck: PasswordCheck
    @Inject lateinit var nsSettingStatus: NSSettingsStatus
    @Inject lateinit var openHumansUploaderPlugin: OpenHumansUploaderPlugin
    @Inject lateinit var diaconnG8Plugin: DiaconnG8Plugin
    @Inject lateinit var garminPlugin: GarminPlugin

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun setArguments(args: Bundle?) {
        super.setArguments(args)
        pluginId = args?.getInt("id") ?: -1
        filter = args?.getString("filter") ?: ""
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("id", pluginId)
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

    private fun addPreferencesFromResourceIfEnabled(p: PluginBase?, rootKey: String?, enabled: Boolean) {
        if (enabled) addPreferencesFromResourceIfEnabled(p, rootKey)
    }

    private fun addPreferencesFromResourceIfEnabled(p: PluginBase?, rootKey: String?) {
        if (p!!.isEnabled() && p.preferencesId != -1)
            addPreferencesFromResource(p.preferencesId, rootKey)
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
            if (bundle.containsKey("id")) {
                pluginId = bundle.getInt("id")
            }
            if (bundle.containsKey("filter")) {
                filter = bundle.getString("filter") ?: ""
            }
        }
        if (pluginId != -1) {
            addPreferencesFromResource(pluginId, rootKey)
        } else {
            addPreferencesFromResource(R.xml.pref_general, rootKey)
            addPreferencesFromResource(app.aaps.plugins.main.R.xml.pref_overview, rootKey)
            addPreferencesFromResourceIfEnabled(safetyPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(eversensePlugin, rootKey)
            addPreferencesFromResourceIfEnabled(dexcomPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(tomatoPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(glunovoPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(intelligoPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(poctechPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(aidexPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(glimpPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(loopPlugin, rootKey, config.APS)
            addPreferencesFromResourceIfEnabled(openAPSAMAPlugin, rootKey, config.APS)
            addPreferencesFromResourceIfEnabled(openAPSSMBPlugin, rootKey, config.APS)
            addPreferencesFromResourceIfEnabled(openAPSSMBDynamicISFPlugin, rootKey, config.APS)
            addPreferencesFromResourceIfEnabled(sensitivityAAPSPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(sensitivityWeightedAveragePlugin, rootKey)
            addPreferencesFromResourceIfEnabled(sensitivityOref1Plugin, rootKey)
            addPreferencesFromResourceIfEnabled(danaRPlugin, rootKey, config.PUMPDRIVERS)
            addPreferencesFromResourceIfEnabled(danaRKoreanPlugin, rootKey, config.PUMPDRIVERS)
            addPreferencesFromResourceIfEnabled(danaRv2Plugin, rootKey, config.PUMPDRIVERS)
            addPreferencesFromResourceIfEnabled(danaRSPlugin, rootKey, config.PUMPDRIVERS)
            addPreferencesFromResourceIfEnabled(localInsightPlugin, rootKey, config.PUMPDRIVERS)
            addPreferencesFromResourceIfEnabled(comboPlugin, rootKey, config.PUMPDRIVERS)
            addPreferencesFromResourceIfEnabled(combov2Plugin, rootKey, config.PUMPDRIVERS)
            addPreferencesFromResourceIfEnabled(medtronicPumpPlugin, rootKey, config.PUMPDRIVERS)
            addPreferencesFromResourceIfEnabled(diaconnG8Plugin, rootKey, config.PUMPDRIVERS)
            addPreferencesFromResourceIfEnabled(eopatchPumpPlugin, rootKey, config.PUMPDRIVERS)
            addPreferencesFromResourceIfEnabled(medtrumPlugin, rootKey, config.PUMPDRIVERS)
            addPreferencesFromResource(R.xml.pref_pump, rootKey, config.PUMPDRIVERS)
            addPreferencesFromResourceIfEnabled(virtualPumpPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(insulinOrefFreePeakPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(nsClientPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(nsClientV3Plugin, rootKey)
            addPreferencesFromResourceIfEnabled(tidepoolPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(smsCommunicatorPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(automationPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(autotunePlugin, rootKey)
            addPreferencesFromResourceIfEnabled(wearPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(statusLinePlugin, rootKey)
            addPreferencesFromResource(R.xml.pref_alerts, rootKey)
            addPreferencesFromResource(app.aaps.plugins.configuration.R.xml.pref_datachoices, rootKey)
            addPreferencesFromResourceIfEnabled(maintenancePlugin, rootKey)
            addPreferencesFromResourceIfEnabled(openHumansUploaderPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(garminPlugin, rootKey)
        }
        initSummary(preferenceScreen, pluginId != -1)
        preprocessPreferences()
        if (filter != "") updateFilterVisibility(filter, preferenceScreen)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        key ?: return
        rxBus.send(EventPreferenceChange(key))
        if (key == rh.gs(app.aaps.core.ui.R.string.key_language)) {
            rxBus.send(EventRebuildTabs(true))
            //recreate() does not update language so better close settings
            activity?.finish()
        }
        if (key == rh.gs(app.aaps.plugins.main.R.string.key_short_tabtitles)) {
            rxBus.send(EventRebuildTabs())
        }
        if (key == rh.gs(app.aaps.core.utils.R.string.key_units)) {
            activity?.recreate()
            return
        }
        if (key == rh.gs(app.aaps.core.utils.R.string.key_use_autosens) && sp.getBoolean(app.aaps.core.utils.R.string.key_use_autosens, false)) {
            activity?.let {
                OKDialog.show(it, rh.gs(app.aaps.plugins.configuration.R.string.configbuilder_sensitivity), rh.gs(R.string.sensitivity_warning))
            }
        }
        checkForBiometricFallback(key)

        updatePrefSummary(findPreference(key))
        preprocessPreferences()
    }

    private fun preprocessPreferences() {
        for (plugin in pluginStore.plugins) {
            if (plugin.isEnabled()) plugin.preprocessPreferences(this)
        }
    }

    private fun checkForBiometricFallback(key: String) {
        // Biometric protection activated without set master password
        if ((rh.gs(app.aaps.core.utils.R.string.key_settings_protection) == key ||
                rh.gs(app.aaps.core.utils.R.string.key_application_protection) == key ||
                rh.gs(app.aaps.core.utils.R.string.key_bolus_protection) == key) &&
            sp.getString(app.aaps.core.utils.R.string.key_master_password, "") == "" &&
            sp.getInt(key, NONE.ordinal) == BIOMETRIC.ordinal
        ) {
            activity?.let {
                val title = rh.gs(app.aaps.core.ui.R.string.unsecure_fallback_biometric)
                val message =
                    rh.gs(
                        app.aaps.plugins.configuration.R.string.master_password_missing,
                        rh.gs(app.aaps.plugins.configuration.R.string.configbuilder_general),
                        rh.gs(app.aaps.plugins.configuration.R.string.protection)
                    )
                OKDialog.show(it, title = title, message = message)
            }
        }

        // Master password erased with activated Biometric protection
        val isBiometricActivated = sp.getInt(app.aaps.core.utils.R.string.key_settings_protection, NONE.ordinal) == BIOMETRIC.ordinal ||
            sp.getInt(app.aaps.core.utils.R.string.key_application_protection, NONE.ordinal) == BIOMETRIC.ordinal ||
            sp.getInt(app.aaps.core.utils.R.string.key_bolus_protection, NONE.ordinal) == BIOMETRIC.ordinal
        if (rh.gs(app.aaps.core.utils.R.string.key_master_password) == key && sp.getString(key, "") == "" && isBiometricActivated) {
            activity?.let {
                val title = rh.gs(app.aaps.core.ui.R.string.unsecure_fallback_biometric)
                val message = rh.gs(app.aaps.core.ui.R.string.unsecure_fallback_descriotion_biometric)
                OKDialog.show(it, title = title, message = message)
            }
        }
    }

    private fun addPreferencesFromResource(@Suppress("SameParameterValue") @XmlRes preferencesResId: Int, key: String?, enabled: Boolean) {
        if (enabled) addPreferencesFromResource(preferencesResId, key)
    }

    @SuppressLint("RestrictedApi")
    private fun addPreferencesFromResource(@XmlRes preferencesResId: Int, key: String?) {
        context?.let { context ->
            val xmlRoot = preferenceManager.inflateFromResource(context, preferencesResId, null)
            val root: Preference?
            if (key != null) {
                root = xmlRoot.findPreference(key)
                if (root == null) return
                require(root is PreferenceScreen) {
                    ("Preference object with key $key is not a PreferenceScreen")
                }
                preferenceScreen = root
            } else {
                addPreferencesFromResource(preferencesResId)
            }
        }
    }

    private fun adjustUnitDependentPrefs(pref: Preference) { // convert preferences values to current units
        val unitDependent = arrayOf(
            rh.gs(app.aaps.core.utils.R.string.key_hypo_target),
            rh.gs(app.aaps.core.utils.R.string.key_activity_target),
            rh.gs(app.aaps.core.utils.R.string.key_eatingsoon_target),
            rh.gs(app.aaps.core.utils.R.string.key_high_mark),
            rh.gs(app.aaps.core.utils.R.string.key_low_mark)
        )
        if (unitDependent.toList().contains(pref.key) && pref is EditTextPreference) {
            val converted = profileUtil.valueInCurrentUnitsDetect(SafeParse.stringToDouble(pref.text))
            pref.summary = converted.toString()
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
            if (pref.getKey() == rh.gs(app.aaps.core.utils.R.string.key_settings_protection)) {
                val pass: Preference? = findPreference(rh.gs(app.aaps.core.utils.R.string.key_settings_password))
                val usePassword = pref.value == CUSTOM_PASSWORD.ordinal.toString()
                pass?.let { it.isVisible = usePassword }
                val pin: Preference? = findPreference(rh.gs(app.aaps.core.utils.R.string.key_settings_pin))
                val usePin = pref.value == CUSTOM_PIN.ordinal.toString()
                pin?.let { it.isVisible = usePin }
            }
            // Application
            if (pref.getKey() == rh.gs(app.aaps.core.utils.R.string.key_application_protection)) {
                val pass: Preference? = findPreference(rh.gs(app.aaps.core.utils.R.string.key_application_password))
                val usePassword = pref.value == CUSTOM_PASSWORD.ordinal.toString()
                pass?.let { it.isVisible = usePassword }
                val pin: Preference? = findPreference(rh.gs(app.aaps.core.utils.R.string.key_application_pin))
                val usePin = pref.value == CUSTOM_PIN.ordinal.toString()
                pin?.let { it.isVisible = usePin }
            }
            // Bolus
            if (pref.getKey() == rh.gs(app.aaps.core.utils.R.string.key_bolus_protection)) {
                val pass: Preference? = findPreference(rh.gs(app.aaps.core.utils.R.string.key_bolus_password))
                val usePassword = pref.value == CUSTOM_PASSWORD.ordinal.toString()
                pass?.let { it.isVisible = usePassword }
                val pin: Preference? = findPreference(rh.gs(app.aaps.core.utils.R.string.key_bolus_pin))
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

        for (plugin in pluginStore.plugins) {
            pref?.let { it.key?.let { plugin.updatePreferenceSummary(pref) } }
        }

        val hmacPasswords = arrayOf(
            rh.gs(app.aaps.core.utils.R.string.key_bolus_password),
            rh.gs(app.aaps.core.utils.R.string.key_master_password),
            rh.gs(app.aaps.core.utils.R.string.key_application_password),
            rh.gs(app.aaps.core.utils.R.string.key_settings_password),
            rh.gs(app.aaps.core.utils.R.string.key_bolus_pin),
            rh.gs(app.aaps.core.utils.R.string.key_application_pin),
            rh.gs(app.aaps.core.utils.R.string.key_settings_pin)
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
        context?.let { context ->
            when (preference.key) {
                rh.gs(app.aaps.core.utils.R.string.key_master_password)      -> {
                    passwordCheck.queryPassword(context, app.aaps.plugins.configuration.R.string.current_master_password, app.aaps.core.utils.R.string.key_master_password, {
                        passwordCheck.setPassword(context, app.aaps.core.ui.R.string.master_password, app.aaps.core.utils.R.string.key_master_password)
                    })
                    true
                }

                rh.gs(app.aaps.core.utils.R.string.key_settings_password)    -> {
                    passwordCheck.setPassword(context, app.aaps.core.ui.R.string.settings_password, app.aaps.core.utils.R.string.key_settings_password)
                    true
                }

                rh.gs(app.aaps.core.utils.R.string.key_bolus_password)       -> {
                    passwordCheck.setPassword(context, app.aaps.core.ui.R.string.bolus_password, app.aaps.core.utils.R.string.key_bolus_password)
                    true
                }

                rh.gs(app.aaps.core.utils.R.string.key_application_password) -> {
                    passwordCheck.setPassword(context, app.aaps.core.ui.R.string.application_password, app.aaps.core.utils.R.string.key_application_password)
                    true
                }

                rh.gs(app.aaps.core.utils.R.string.key_settings_pin)         -> {
                    passwordCheck.setPassword(context, app.aaps.core.ui.R.string.settings_pin, app.aaps.core.utils.R.string.key_settings_pin, pinInput = true)
                    true
                }

                rh.gs(app.aaps.core.utils.R.string.key_bolus_pin)            -> {
                    passwordCheck.setPassword(context, app.aaps.core.ui.R.string.bolus_pin, app.aaps.core.utils.R.string.key_bolus_pin, pinInput = true)
                    true
                }

                rh.gs(app.aaps.core.utils.R.string.key_application_pin)      -> {
                    passwordCheck.setPassword(context, app.aaps.core.ui.R.string.application_pin, app.aaps.core.utils.R.string.key_application_pin, pinInput = true)
                    true
                }
                // NSClient copy settings
                rh.gs(app.aaps.plugins.main.R.string.key_statuslights_copy_ns)      -> {
                    nsSettingStatus.copyStatusLightsNsSettings(context)
                    true
                }

                else                                                                -> super.onPreferenceTreeClick(preference)
            }
        } ?: super.onPreferenceTreeClick(preference)

    fun setFilter(filter: String) {
        this.filter = filter
        preferenceManager?.preferenceScreen?.let { updateFilterVisibility(filter, it) }
    }
}
