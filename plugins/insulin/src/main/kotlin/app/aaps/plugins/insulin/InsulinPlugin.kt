package app.aaps.plugins.insulin

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConcentrationChange
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.DoubleNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.fromJsonObject
import app.aaps.core.objects.extensions.toJsonObject
import app.aaps.core.ui.compose.icons.IcPluginInsulin
import app.aaps.core.ui.toast.ToastUtils
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by Philoul on 29.12.2024.
 */

@Singleton
class InsulinPlugin @Inject constructor(
    val preferences: Preferences,
    rh: ResourceHelper,
    val profileFunction: ProfileFunction,
    val rxBus: RxBus,
    aapsLogger: AAPSLogger,
    val config: Config,
    val hardLimits: HardLimits,
    val uiInteraction: UiInteraction,
    val uel: UserEntryLogger,
    val activePlugin: ActivePlugin,
    val aapsSchedulers: AapsSchedulers,
    val fabricPrivacy: FabricPrivacy,
    val persistenceLayer: PersistenceLayer,
    val notificationManager: NotificationManager,
    @ApplicationScope private val appScope: CoroutineScope
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.INSULIN)
        .fragmentClass(InsulinNewFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_insulin)
        .icon(IcPluginInsulin)
        .pluginName(app.aaps.core.interfaces.R.string.insulin_plugin)
        .shortName(app.aaps.core.interfaces.R.string.insulin_shortname)
        .setDefault(true)
        .visibleByDefault(true)
        .enableByDefault(true)
        .neverVisible(config.AAPSCLIENT)
        .description(R.string.description_insulin_plugin),
    aapsLogger, rh
), Insulin {

    override val id = InsulinType.UNKNOWN
    override val friendlyName get(): String = rh.gs(app.aaps.core.interfaces.R.string.insulin_plugin)

    override val iCfg: ICfg
        get() {
            val profile = profileFunction.getProfile()
            return profile?.iCfg ?: insulins[0]
        }

    override val comment: String
        get() = TODO("Not yet implemented")

    lateinit var currentInsulin: ICfg
    private var lastWarned: Long = 0
    private var disposable: CompositeDisposable = CompositeDisposable()
    private val currentConcentration: Double
        get()= preferences.get(DoubleNonKey.ApprovedConcentration)
    private val targetConcentration: Double
        get()= preferences.get(DoubleNonKey.NewConcentration)
    private val isU100
        get() = currentConcentration == DEFAULTCONCENTRATION && targetConcentration == DEFAULTCONCENTRATION
    private val concentrationConfirmed: Boolean
        get()= (preferences.get(LongNonKey.LastInsulinChange) < preferences.get(LongNonKey.LastInsulinConfirmation)) || isU100
    val MAX_INSULIN = T.days(7).msecs()
    private val recentUpdate: Boolean
        get()=preferences.get(LongNonKey.LastInsulinChange) > System.currentTimeMillis() - T.mins(15).msecs()
    private val DEFAULTCONCENTRATION = 1.0

    var insulins: ArrayList<ICfg> = ArrayList()
    var currentInsulinIndex = 0
    val numOfInsulins get() = insulins.size

    override fun onStart() {
        super.onStart()
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        loadSettings()
        // TherapyEvent changes
        persistenceLayer.observeChanges(TE::class.java)
            .onEach { swapAdapter() }
            .launchIn(newScope)
        swapAdapter()
        if (!config.enableInsulinConcentration() && !config.isEngineeringMode()) { // Concentration not allowed without engineering mode
            preferences.put(DoubleNonKey.NewConcentration, DEFAULTCONCENTRATION)
        }
    }

    override fun onStop() {
        super.onStop()
        disposable.clear()
    }

    @Synchronized
    fun swapAdapter() { // Launch Popup to confirm Insulin concentration on Reservoir change
        val now = System.currentTimeMillis()

        appScope.launch {
            val list = withContext(Dispatchers.IO) {
                persistenceLayer.getTherapyEventDataFromTime(now - MAX_INSULIN, false)
            }
            list.filter { te -> te.type == TE.Type.INSULIN_CHANGE }
                .maxByOrNull { it.timestamp }
                ?.let { preferences.put(LongNonKey.LastInsulinChange, it.timestamp) }

            val showOnUpdateRequest = (currentConcentration != targetConcentration) && recentUpdate
            if (!concentrationConfirmed || showOnUpdateRequest) {
                uiInteraction.runInsulinConfirmation()
            }
        }
    }

    override fun insulinList(concentration: Double?): List<CharSequence> {
        return insulins.filter {
            when {
                concentration == null -> it.concentration == preferences.get(DoubleNonKey.ApprovedConcentration)
                concentration == 0.0  -> true
                concentration > 0.0   -> it.concentration == concentration
                else                  -> false
            }
        }.map {
            it.insulinLabel
        }
    }

    fun setCurrent(iCfg: ICfg): Int {
        insulins.forEachIndexed { index, it ->
            if (iCfg.isEqual(it)) {
                currentInsulinIndex = index
                currentInsulin = deepClone(currentInsulin())
                return index
            }
        }
        addNewInsulin(iCfg)
        currentInsulin = deepClone(currentInsulin()).also { it.insulinTemplate = InsulinType.fromPeak(it.insulinPeakTime).ordinal }
        return insulins.size - 1
    }

    override fun getInsulin(insulinLabel: String): ICfg {
        insulins.forEach {
            if (it.insulinLabel == insulinLabel)
                return it
        }
        return iCfg     // if no insulin found then return current running iCfg
    }

    override fun getDefaultInsulin(concentration: Double?): ICfg {
        if (concentration == null || concentration == iCfg.concentration) return iCfg  // without null or current concentration, return current running iCfg
        return insulins.firstOrNull { it.concentration == concentration } ?: iCfg
    }

    override fun approveConcentration(concentration: Double) {
        if ((recentUpdate || !concentrationConfirmed) && concentration == targetConcentration) {
            val now = System.currentTimeMillis()
            preferences.put(DoubleNonKey.ApprovedConcentration, concentration)
            preferences.put(LongNonKey.LastInsulinConfirmation, now)
            rxBus.send(EventConcentrationChange())
        }
    }

    fun insulinTemplateList(): List<InsulinType> = listOf(
        InsulinType.OREF_RAPID_ACTING,
        InsulinType.OREF_ULTRA_RAPID_ACTING,
        InsulinType.OREF_LYUMJEV,
        InsulinType.OREF_FREE_PEAK
    )

    fun insulinTemplateLabelList(): List<CharSequence> = insulinTemplateList().map { rh.gs(it.label) }

    fun concentrationList(): List<ConcentrationType> = listOf(
        ConcentrationType.U10,
        ConcentrationType.U50,
        ConcentrationType.U100,
        ConcentrationType.U200
    )

    fun concentrationLabelList(): List<CharSequence> = concentrationList().map { rh.gs(it.label) }

    fun sendShortDiaNotification(dia: Double) {
        // Todo Check if we need this kind of function to send notification
        if (System.currentTimeMillis() - lastWarned > 60 * 1000) {
            lastWarned = System.currentTimeMillis()
            notificationManager.post(NotificationId.SHORT_DIA, String.format(notificationPattern, dia, hardLimits.minDia()))
        }
    }

    private val notificationPattern: String
        get() = rh.gs(R.string.dia_too_short)

    @Synchronized
    fun addNewInsulin(newICfg: ICfg, ue: Boolean = true): ICfg {
        if (newICfg.insulinLabel == "" || insulinLabelAlreadyExists(newICfg.insulinLabel))
            newICfg.insulinLabel = createNewInsulinLabel(newICfg)
        val newInsulin = deepClone(newICfg)
        if (insulins.size > 0 && newInsulin.isEqual(iCfg)) {
            insulins.add(0, iCfg)
            currentInsulinIndex = 0
        } else {
            insulins.add(newInsulin)
            currentInsulinIndex = insulins.size - 1
        }
        if (ue)
            uel.log(Action.NEW_INSULIN, Sources.Insulin, value = ValueWithUnit.SimpleString(newICfg.insulinLabel))
        currentInsulin = deepClone(newInsulin)
        currentInsulin.insulinTemplate = 0
        storeSettings()
        return newInsulin
    }

    @Synchronized
    fun removeCurrentInsulin(activity: FragmentActivity?) {
        // activity included to include PopUp or Toast when Remove can't be done (default insulin or insulin used within profile
        val insulinRemoved = currentInsulin().insulinLabel
        insulins.removeAt(currentInsulinIndex)
        uel.log(Action.INSULIN_REMOVED, Sources.Insulin, value = ValueWithUnit.SimpleString(insulinRemoved))
        currentInsulinIndex = 0     // Current running iCfg put in first position
        currentInsulin = deepClone(currentInsulin())
        storeSettings()
    }

    fun createNewInsulinLabel(iCfg: ICfg, currentIndex: Int = -1, template: InsulinType? = null): String {
        val template = template ?: InsulinType.fromPeak(iCfg.insulinPeakTime)
        var insulinLabel = rh.gs(template.label)
        if (insulinLabelAlreadyExists(insulinLabel, currentIndex)) {
            for (i in 1..10000) {
                if (!insulinLabelAlreadyExists("${insulinLabel}_$i", currentIndex)) {
                    insulinLabel = "${insulinLabel}_$i"
                    break
                }
            }
        }
        return insulinLabel
    }

    private fun insulinLabelAlreadyExists(insulinLabel: String, currentIndex: Int = -1): Boolean {
        insulins.forEachIndexed { index, insulin ->
            if (index != currentIndex) {
                if (insulin.insulinLabel == insulinLabel) {
                    return true
                }
            }
        }
        return false
    }

    private fun insulinAlreadyExists(iCfg: ICfg, currentIndex: Int = -1): Boolean {
        insulins.forEachIndexed { index, insulin ->
            if (index != currentIndex) {
                if (iCfg.isEqual(insulin)) {
                    return true
                }
            }
        }
        return false
    }

    @Synchronized
    fun loadSettings() {
        val jsonString = preferences.get(StringNonKey.InsulinConfiguration)
        val jsonObject = runCatching {
            Json.parseToJsonElement(jsonString) as? JsonObject
        }.getOrNull()
        applyConfiguration(jsonObject ?: buildJsonObject {})
    }

    @Synchronized
    fun storeSettings() {
        preferences.put(StringNonKey.InsulinConfiguration, configuration().toString())
    }

    @Synchronized
    override fun configuration(): JsonObject {
        val jsonArray = buildJsonArray {
            insulins.forEach {
                try {
                    add(it.toJsonObject())
                } catch (_: Exception) {
                    //
                }
            }
        }
        return buildJsonObject {
            put("insulins", jsonArray)
        }
    }

    @Synchronized
    override fun applyConfiguration(configuration: JsonObject) {
        insulins.clear()

        val insulinsArray = configuration["insulins"] as? JsonArray
        if (insulinsArray == null || insulinsArray.isEmpty()) {
            // new install
            addNewInsulin(InsulinType.OREF_RAPID_ACTING.getICfg(rh).also { it.insulinTemplate = InsulinType.OREF_RAPID_ACTING.ordinal })
            return
        }

        insulinsArray.forEach { jsonElement ->
            try {
                val jsonObject = jsonElement as? JsonObject ?: return@forEach
                val newICfg = ICfg.fromJsonObject(jsonObject) // Supposant que fromJson accepte maintenant JsonObject
                if (!insulinAlreadyExists(newICfg)) // No Duplicated Insulin Allowed
                    addNewInsulin(newICfg, newICfg.insulinLabel.isEmpty())
            } catch (_: Exception) {
                //
            }
        }
    }

    @Synchronized
    fun isValidEditState(activity: FragmentActivity?, verbose: Boolean = true): Boolean {
        with(currentInsulin) {
            if (dia < hardLimits.minDia() || dia > hardLimits.maxDia()) {
                if (verbose)
                    ToastUtils.errorToast(activity, rh.gs(app.aaps.core.ui.R.string.value_out_of_hard_limits, rh.gs(app.aaps.core.ui.R.string.insulin_dia), dia))
                return false
            }
            if (peak < hardLimits.minPeak() || peak > hardLimits.maxPeak()) {
                if (verbose)
                    ToastUtils.errorToast(activity, rh.gs(app.aaps.core.ui.R.string.value_out_of_hard_limits, rh.gs(app.aaps.core.ui.R.string.insulin_peak), peak))
                return false
            }
            if (insulinLabel.isEmpty()) {
                if (verbose)
                    ToastUtils.errorToast(activity, rh.gs(R.string.missing_insulin_name))
                return false
            }
            // Check Inulin name is unique and insulin parameters is unique
            if (insulinLabelAlreadyExists(this.insulinLabel, currentInsulinIndex)) {
                if (verbose)
                    ToastUtils.errorToast(activity, rh.gs(R.string.insulin_name_exists, insulinLabel))
                return false
            }
            if (insulinAlreadyExists(this, currentInsulinIndex)) {
                if (verbose)
                    ToastUtils.errorToast(activity, rh.gs(R.string.insulin_duplicated, insulinLabel))
                return false
            }
        }
        return true
    }

    fun currentInsulin(): ICfg = insulins[currentInsulinIndex]

    fun deepClone(iCfg: ICfg, withoutName: Boolean = false): ICfg = iCfg.deepClone().also {
        if (withoutName)
            it.insulinLabel = ""
    }

    val hasUnsavedChanges: Boolean
        get() = !currentInsulin.isEqual(currentInsulin()) || currentInsulin.insulinLabel != currentInsulin().insulinLabel || currentInsulin.isNew

    val canRemoveCurrentInsulin: Boolean
        get() = numOfInsulins > 1 && !currentInsulin().isEqual(iCfg)

    val currentInsulinDisplayName: String
        get() = if (currentInsulin().isEqual(iCfg))
            rh.gs(R.string.pump_insulin, currentInsulin().insulinLabel)
        else
            currentInsulin().insulinLabel

    fun getTemplateDisplayName(template: InsulinType): String = rh.gs(template.label)

    val isPeakEditable: Boolean
        get() = InsulinType.fromInt(currentInsulin.insulinTemplate) == InsulinType.OREF_FREE_PEAK

    fun getAvailableConcentrations(): List<ConcentrationType> {
        val availableConcentrations = mutableSetOf<Double>()
        insulins.forEach { availableConcentrations.add(it.concentration) }

        return concentrationList().filter { concentrationType ->
            availableConcentrations.contains(concentrationType.value)
        }
    }

    fun getAvailableConcentrationLabels(): List<CharSequence> {
        return getAvailableConcentrations().map { rh.gs(it.label) }
    }

    val hasMultipleConcentrations: Boolean
        get() = getAvailableConcentrations().size > 1

}