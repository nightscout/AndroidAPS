package info.nightscout.androidaps.activities

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import androidx.annotation.XmlRes
import androidx.preference.*
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import dagger.android.support.AndroidSupportInjection
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.events.EventRebuildTabs
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import info.nightscout.androidaps.plugins.aps.openAPSMA.OpenAPSMAPlugin
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.constraints.safety.SafetyPlugin
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin
import info.nightscout.androidaps.plugins.general.careportal.CareportalPlugin
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.plugins.general.tidepool.TidepoolPlugin
import info.nightscout.androidaps.plugins.general.wear.WearPlugin
import info.nightscout.androidaps.plugins.general.xdripStatusline.StatusLinePlugin
import info.nightscout.androidaps.plugins.insulin.InsulinOrefFreePeakPlugin
import info.nightscout.androidaps.plugins.pump.combo.ComboPlugin
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin
import info.nightscout.androidaps.plugins.pump.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref0Plugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin
import info.nightscout.androidaps.plugins.source.DexcomPlugin
import info.nightscout.androidaps.plugins.source.EversensePlugin
import info.nightscout.androidaps.plugins.source.GlimpPlugin
import info.nightscout.androidaps.plugins.source.PoctechPlugin
import info.nightscout.androidaps.plugins.source.TomatoPlugin
import info.nightscout.androidaps.utils.OKDialog.show
import info.nightscout.androidaps.utils.SafeParse
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject

class MyPreferenceFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener, HasAndroidInjector {
    private var pluginId = -1

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var sp: SP

    @Inject lateinit var automationPlugin: AutomationPlugin
    @Inject lateinit var danaRPlugin: DanaRPlugin
    @Inject lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Inject lateinit var danaRv2Plugin: DanaRv2Plugin
    @Inject lateinit var danaRSPlugin: DanaRSPlugin
    @Inject lateinit var careportalPlugin: CareportalPlugin
    @Inject lateinit var insulinOrefFreePeakPlugin: InsulinOrefFreePeakPlugin
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var openAPSAMAPlugin: OpenAPSAMAPlugin
    @Inject lateinit var openAPSMAPlugin: OpenAPSMAPlugin
    @Inject lateinit var openAPSSMBPlugin: OpenAPSSMBPlugin
    @Inject lateinit var safetyPlugin: SafetyPlugin
    @Inject lateinit var dexcomPlugin: DexcomPlugin
    @Inject lateinit var eversensePlugin: EversensePlugin
    @Inject lateinit var glimpPlugin: GlimpPlugin
    @Inject lateinit var poctechPlugin: PoctechPlugin
    @Inject lateinit var tomatoPlugin: TomatoPlugin
    @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    @Inject lateinit var statusLinePlugin: StatusLinePlugin
    @Inject lateinit var tidepoolPlugin: TidepoolPlugin
    @Inject lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Inject lateinit var wearPlugin: WearPlugin

    @Inject lateinit var androidInjector: DispatchingAndroidInjector<Any>

    override fun androidInjector(): AndroidInjector<Any> = androidInjector

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun setArguments(args: Bundle?) {
        super.setArguments(args)
        pluginId = args?.getInt("id") ?: -1
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("id", pluginId)
    }

    private fun addPreferencesFromResourceIfEnabled(p: PluginBase?, rootKey: String?) {
        if (p!!.isEnabled() && p.preferencesId != -1)
            addPreferencesFromResource(p.preferencesId, rootKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceManager
            .getDefaultSharedPreferences(context)
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (savedInstanceState != null && savedInstanceState.containsKey("id")) {
            pluginId = savedInstanceState.getInt("id")
        }
        if (arguments != null && arguments!!.containsKey("id")) {
            pluginId = arguments!!.getInt("id")
        }
        if (pluginId != -1) {
            addPreferencesFromResource(pluginId, rootKey)
        } else {
            if (!Config.NSCLIENT) {
                addPreferencesFromResource(R.xml.pref_password, rootKey)
            }
            addPreferencesFromResource(R.xml.pref_general, rootKey)
            addPreferencesFromResource(R.xml.pref_age, rootKey)
            addPreferencesFromResource(R.xml.pref_overview, rootKey)
            addPreferencesFromResourceIfEnabled(eversensePlugin, rootKey)
            addPreferencesFromResourceIfEnabled(dexcomPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(tomatoPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(poctechPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(glimpPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(careportalPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(safetyPlugin, rootKey)
            if (Config.APS) {
                addPreferencesFromResourceIfEnabled(loopPlugin, rootKey)
                addPreferencesFromResourceIfEnabled(openAPSMAPlugin, rootKey)
                addPreferencesFromResourceIfEnabled(openAPSAMAPlugin, rootKey)
                addPreferencesFromResourceIfEnabled(openAPSSMBPlugin, rootKey)
            }
            addPreferencesFromResourceIfEnabled(SensitivityAAPSPlugin.getPlugin(), rootKey)
            addPreferencesFromResourceIfEnabled(SensitivityWeightedAveragePlugin.getPlugin(), rootKey)
            addPreferencesFromResourceIfEnabled(SensitivityOref0Plugin.getPlugin(), rootKey)
            addPreferencesFromResourceIfEnabled(SensitivityOref1Plugin.getPlugin(), rootKey)
            if (Config.PUMPDRIVERS) {
                addPreferencesFromResourceIfEnabled(danaRPlugin, rootKey)
                addPreferencesFromResourceIfEnabled(danaRKoreanPlugin, rootKey)
                addPreferencesFromResourceIfEnabled(danaRv2Plugin, rootKey)
                addPreferencesFromResourceIfEnabled(danaRSPlugin, rootKey)
                addPreferencesFromResourceIfEnabled(LocalInsightPlugin.getPlugin(), rootKey)
                addPreferencesFromResourceIfEnabled(ComboPlugin.getPlugin(), rootKey)
                addPreferencesFromResourceIfEnabled(MedtronicPumpPlugin.getPlugin(), rootKey)
            }
            if (!Config.NSCLIENT) {
                addPreferencesFromResourceIfEnabled(virtualPumpPlugin, rootKey)
            }
            addPreferencesFromResourceIfEnabled(insulinOrefFreePeakPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(NSClientPlugin.getPlugin(), rootKey)
            addPreferencesFromResourceIfEnabled(tidepoolPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(smsCommunicatorPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(automationPlugin, rootKey)
            addPreferencesFromResource(R.xml.pref_others, rootKey)
            addPreferencesFromResource(R.xml.pref_datachoices, rootKey)
            addPreferencesFromResourceIfEnabled(wearPlugin, rootKey)
            addPreferencesFromResourceIfEnabled(statusLinePlugin, rootKey)
        }
        initSummary(preferenceScreen)
        for (plugin in MainApp.getPluginsList()) {
            plugin.preprocessPreferences(this)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        rxBus.send(EventPreferenceChange(key))
        if (key == resourceHelper.gs(R.string.key_language)) {
            rxBus.send(EventRebuildTabs(true))
            //recreate() does not update language so better close settings
            activity?.finish()
        }
        if (key == resourceHelper.gs(R.string.key_short_tabtitles)) {
            rxBus.send(EventRebuildTabs())
        }
        if (key == resourceHelper.gs(R.string.key_units)) {
            activity?.recreate()
            return
        }
        if (key == resourceHelper.gs(R.string.key_openapsama_useautosens) && sp.getBoolean(R.string.key_openapsama_useautosens, false))
            activity?.let {
                show(it, resourceHelper.gs(R.string.configbuilder_sensitivity), resourceHelper.gs(R.string.sensitivity_warning))
            }

        updatePrefSummary(findPreference(key))
    }

    fun addPreferencesFromResource(@XmlRes preferencesResId: Int, key: String?) {
        val xmlRoot = preferenceManager.inflateFromResource(context,
            preferencesResId, null)
        val root: Preference?
        if (key != null) {
            root = xmlRoot.findPreference(key)
            if (root == null) return
            require(root is PreferenceScreen) {
                ("Preference object with key " + key
                    + " is not a PreferenceScreen")
            }
            preferenceScreen = root
        } else {
            addPreferencesFromResource(preferencesResId)
        }
    }

    private fun adjustUnitDependentPrefs(pref: Preference) { // convert preferences values to current units
        val unitDependent = arrayOf(
            resourceHelper.gs(R.string.key_hypo_target),
            resourceHelper.gs(R.string.key_activity_target),
            resourceHelper.gs(R.string.key_eatingsoon_target),
            resourceHelper.gs(R.string.key_high_mark),
            resourceHelper.gs(R.string.key_low_mark)
        )
        if (Arrays.asList(*unitDependent).contains(pref.key)) {
            val editTextPref = pref as EditTextPreference
            val converted = Profile.toCurrentUnitsString(SafeParse.stringToDouble(editTextPref.text))
            editTextPref.summary = converted
            editTextPref.text = converted
        }
    }

    private fun updatePrefSummary(pref: Preference?) {
        if (pref is ListPreference) {
            pref.setSummary(pref.entry)
        }
        if (pref is EditTextPreference) {
            val editTextPref = pref
            if (pref.getKey().contains("password") || pref.getKey().contains("secret")) {
                pref.setSummary("******")
            } else if (editTextPref.text != null) {
                pref.dialogMessage = editTextPref.dialogMessage
                pref.setSummary(editTextPref.text)
            } else {
                for (plugin in MainApp.getPluginsList()) {
                    plugin.updatePreferenceSummary(pref)
                }
            }
        }
        pref?.let { adjustUnitDependentPrefs(it) }
    }

    private fun initSummary(p: Preference) {
        p.isIconSpaceReserved = false // remove extra spacing on left after migration to androidx
        if (p is PreferenceGroup) {
            val pGrp = p
            for (i in 0 until pGrp.preferenceCount) {
                initSummary(pGrp.getPreference(i))
            }
        } else {
            updatePrefSummary(p)
        }
    }
}