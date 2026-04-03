package app.aaps.plugins.aps.compose

import androidx.compose.runtime.Immutable
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.interfaces.aps.OapsProfileAutoIsf
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.events.EventOpenAPSUpdateGui
import app.aaps.plugins.aps.events.EventResetOpenAPSGui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Immutable
data class OpenAPSUiState(
    val lastRun: String = "",
    val sections: List<OpenAPSSection> = emptyList(),
    val statusMessage: String = "",
    val isRefreshing: Boolean = false
)

@Immutable
data class OpenAPSSection(
    val titleResId: Int,
    val rows: List<KeyValueRow> = emptyList(),
    val collapsedByDefault: Boolean = false,
    val isGroupHeader: Boolean = false
)

@Immutable
data class KeyValueRow(
    val key: String,
    val value: String
)

class OpenAPSViewModel(
    private val apsPlugin: APS,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val scope: CoroutineScope
) {

    private val _uiState = MutableStateFlow(OpenAPSUiState())
    val uiState: StateFlow<OpenAPSUiState> = _uiState.asStateFlow()

    init {
        rxBus.toFlow(EventOpenAPSUpdateGui::class.java)
            .onEach { updateState() }
            .launchIn(scope)

        rxBus.toFlow(EventResetOpenAPSGui::class.java)
            .onEach { event -> resetState(event.text) }
            .launchIn(scope)

        // Initial state from current data
        updateState()
    }

    fun onRefresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        scope.launch {
            apsPlugin.invoke("OpenAPS pull-to-refresh", false)
        }
    }

    private fun updateState() {
        val lastAPSResult = apsPlugin.lastAPSResult
        if (lastAPSResult == null) {
            _uiState.value = OpenAPSUiState(
                statusMessage = rh.gs(app.aaps.core.ui.R.string.not_available_full)
            )
            return
        }

        val sections = buildList {
            // --- Input parameters group ---
            add(OpenAPSSection(titleResId = R.string.openapsma_input_parameters_label, isGroupHeader = true))

            // Constraints
            lastAPSResult.inputConstraints?.let { constraints ->
                val reasons = constraints.getReasons()
                if (reasons.isNotEmpty()) {
                    add(
                        OpenAPSSection(
                            titleResId = R.string.constraints,
                            rows = reasons.split("\n").filter { it.isNotBlank() }.map { KeyValueRow("", it) }
                        )
                    )
                }
            }

            // Glucose Status
            lastAPSResult.glucoseStatus?.let { gs ->
                add(
                    OpenAPSSection(
                        titleResId = R.string.openapsma_glucose_status_label,
                        rows = gs.toRows()
                    )
                )
            }

            // Current Temp
            lastAPSResult.currentTemp?.let { ct ->
                add(
                    OpenAPSSection(
                        titleResId = R.string.openapsma_current_temp_label,
                        rows = ct.toRows()
                    )
                )
            }

            // IOB Data
            lastAPSResult.iob?.let { iob ->
                add(
                    OpenAPSSection(
                        titleResId = R.string.openapsma_iob_data_label,
                        rows = iob.toRows(lastAPSResult.iobData?.size ?: 0)
                    )
                )
            }

            // Profile (OapsProfile or OapsProfileAutoIsf)
            val profileRows = lastAPSResult.oapsProfileAutoIsf?.toRows()
                ?: lastAPSResult.oapsProfile?.toRows()
            if (profileRows != null) {
                add(
                    OpenAPSSection(
                        titleResId = R.string.openapsma_profile_label,
                        rows = profileRows,
                        collapsedByDefault = true
                    )
                )
            }

            // Meal Data
            lastAPSResult.mealData?.let { md ->
                add(
                    OpenAPSSection(
                        titleResId = R.string.openapsma_meal_data_label,
                        rows = md.toRows()
                    )
                )
            }

            // Autosens Data
            lastAPSResult.autosensResult?.let { asr ->
                add(
                    OpenAPSSection(
                        titleResId = R.string.openapsma_autosensdata_label,
                        rows = asr.toRows()
                    )
                )
            }

            // --- Result group ---
            add(OpenAPSSection(titleResId = app.aaps.core.ui.R.string.result, isGroupHeader = true))

            // Script Debug
            lastAPSResult.scriptDebug?.let { debug ->
                if (debug.isNotEmpty()) {
                    add(
                        OpenAPSSection(
                            titleResId = R.string.openapsma_script_debug_data_label,
                            rows = debug.map { KeyValueRow("", it) },
                            collapsedByDefault = true
                        )
                    )
                }
            }

            // Raw Result (RT)
            val rawData = lastAPSResult.rawData()
            if (rawData is RT) {
                add(
                    OpenAPSSection(
                        titleResId = app.aaps.core.ui.R.string.result,
                        rows = rawData.toRows(),
                        collapsedByDefault = true
                    )
                )
            }

            // Request (algorithm decision) — last section
            val requestText = lastAPSResult.resultAsString()
            if (requestText.isNotEmpty()) {
                add(
                    OpenAPSSection(
                        titleResId = R.string.openapsma_request_label,
                        rows = requestText.lines().filter { it.isNotBlank() }.map { KeyValueRow("", it) }
                    )
                )
            }
        }

        _uiState.value = OpenAPSUiState(
            lastRun = dateUtil.dateAndTimeString(apsPlugin.lastAPSRun),
            sections = sections,
            isRefreshing = false
        )
    }

    private fun resetState(text: String) {
        _uiState.value = OpenAPSUiState(
            statusMessage = text,
            isRefreshing = false
        )
    }

    // --- Mapping functions ---

    private fun GlucoseStatus.toRows(): List<KeyValueRow> = listOf(
        KeyValueRow("glucose", glucose.toString()),
        KeyValueRow("delta", delta.toString()),
        KeyValueRow("shortAvgDelta", shortAvgDelta.toString()),
        KeyValueRow("longAvgDelta", longAvgDelta.toString())
    )

    private fun CurrentTemp.toRows(): List<KeyValueRow> = buildList {
        add(KeyValueRow("duration", duration.toString()))
        add(KeyValueRow("rate", rate.toString()))
        minutesrunning?.let { add(KeyValueRow("minutesrunning", it.toString())) }
    }

    private fun IobTotal.toRows(arraySize: Int): List<KeyValueRow> = buildList {
        add(KeyValueRow("arraySize", rh.gs(R.string.array_of_elements, arraySize)))
        add(KeyValueRow("iob", iob.toString()))
        add(KeyValueRow("activity", activity.toString()))
        add(KeyValueRow("bolussnooze", bolussnooze.toString()))
        add(KeyValueRow("basaliob", basaliob.toString()))
        add(KeyValueRow("netbasalinsulin", netbasalinsulin.toString()))
        add(KeyValueRow("hightempinsulin", hightempinsulin.toString()))
        add(KeyValueRow("lastBolusTime", if (lastBolusTime > 0) dateUtil.dateAndTimeString(lastBolusTime) else "0"))
        add(KeyValueRow("netInsulin", netInsulin.toString()))
        add(KeyValueRow("extendedBolusInsulin", extendedBolusInsulin.toString()))
    }

    private fun OapsProfile.toRows(): List<KeyValueRow> = buildList {
        add(KeyValueRow("dia", dia.toString()))
        add(KeyValueRow("min_5m_carbimpact", min_5m_carbimpact.toString()))
        add(KeyValueRow("max_iob", max_iob.toString()))
        add(KeyValueRow("max_daily_basal", max_daily_basal.toString()))
        add(KeyValueRow("max_basal", max_basal.toString()))
        add(KeyValueRow("min_bg", min_bg.toString()))
        add(KeyValueRow("max_bg", max_bg.toString()))
        add(KeyValueRow("target_bg", target_bg.toString()))
        add(KeyValueRow("carb_ratio", carb_ratio.toString()))
        add(KeyValueRow("sens", sens.toString()))
        add(KeyValueRow("autosens_adjust_targets", autosens_adjust_targets.toString()))
        add(KeyValueRow("max_daily_safety_multiplier", max_daily_safety_multiplier.toString()))
        add(KeyValueRow("current_basal_safety_multiplier", current_basal_safety_multiplier.toString()))
        add(KeyValueRow("high_temptarget_raises_sensitivity", high_temptarget_raises_sensitivity.toString()))
        add(KeyValueRow("low_temptarget_lowers_sensitivity", low_temptarget_lowers_sensitivity.toString()))
        add(KeyValueRow("sensitivity_raises_target", sensitivity_raises_target.toString()))
        add(KeyValueRow("resistance_lowers_target", resistance_lowers_target.toString()))
        add(KeyValueRow("adv_target_adjustments", adv_target_adjustments.toString()))
        add(KeyValueRow("exercise_mode", exercise_mode.toString()))
        add(KeyValueRow("half_basal_exercise_target", half_basal_exercise_target.toString()))
        add(KeyValueRow("maxCOB", maxCOB.toString()))
        add(KeyValueRow("skip_neutral_temps", skip_neutral_temps.toString()))
        add(KeyValueRow("remainingCarbsCap", remainingCarbsCap.toString()))
        add(KeyValueRow("enableUAM", enableUAM.toString()))
        add(KeyValueRow("A52_risk_enable", A52_risk_enable.toString()))
        add(KeyValueRow("SMBInterval", SMBInterval.toString()))
        add(KeyValueRow("enableSMB_with_COB", enableSMB_with_COB.toString()))
        add(KeyValueRow("enableSMB_with_temptarget", enableSMB_with_temptarget.toString()))
        add(KeyValueRow("allowSMB_with_high_temptarget", allowSMB_with_high_temptarget.toString()))
        add(KeyValueRow("enableSMB_always", enableSMB_always.toString()))
        add(KeyValueRow("enableSMB_after_carbs", enableSMB_after_carbs.toString()))
        add(KeyValueRow("maxSMBBasalMinutes", maxSMBBasalMinutes.toString()))
        add(KeyValueRow("maxUAMSMBBasalMinutes", maxUAMSMBBasalMinutes.toString()))
        add(KeyValueRow("bolus_increment", bolus_increment.toString()))
        add(KeyValueRow("carbsReqThreshold", carbsReqThreshold.toString()))
        add(KeyValueRow("current_basal", current_basal.toString()))
        add(KeyValueRow("temptargetSet", temptargetSet.toString()))
        add(KeyValueRow("autosens_max", autosens_max.toString()))
        add(KeyValueRow("out_units", out_units))
        lgsThreshold?.let { add(KeyValueRow("lgsThreshold", it.toString())) }
        add(KeyValueRow("variable_sens", variable_sens.toString()))
        add(KeyValueRow("insulinDivisor", insulinDivisor.toString()))
        add(KeyValueRow("TDD", TDD.toString()))
    }

    private fun OapsProfileAutoIsf.toRows(): List<KeyValueRow> = buildList {
        add(KeyValueRow("dia", dia.toString()))
        add(KeyValueRow("min_5m_carbimpact", min_5m_carbimpact.toString()))
        add(KeyValueRow("max_iob", max_iob.toString()))
        add(KeyValueRow("max_daily_basal", max_daily_basal.toString()))
        add(KeyValueRow("max_basal", max_basal.toString()))
        add(KeyValueRow("min_bg", min_bg.toString()))
        add(KeyValueRow("max_bg", max_bg.toString()))
        add(KeyValueRow("target_bg", target_bg.toString()))
        add(KeyValueRow("carb_ratio", carb_ratio.toString()))
        add(KeyValueRow("sens", sens.toString()))
        add(KeyValueRow("autosens_adjust_targets", autosens_adjust_targets.toString()))
        add(KeyValueRow("max_daily_safety_multiplier", max_daily_safety_multiplier.toString()))
        add(KeyValueRow("current_basal_safety_multiplier", current_basal_safety_multiplier.toString()))
        add(KeyValueRow("high_temptarget_raises_sensitivity", high_temptarget_raises_sensitivity.toString()))
        add(KeyValueRow("low_temptarget_lowers_sensitivity", low_temptarget_lowers_sensitivity.toString()))
        add(KeyValueRow("sensitivity_raises_target", sensitivity_raises_target.toString()))
        add(KeyValueRow("resistance_lowers_target", resistance_lowers_target.toString()))
        add(KeyValueRow("adv_target_adjustments", adv_target_adjustments.toString()))
        add(KeyValueRow("exercise_mode", exercise_mode.toString()))
        add(KeyValueRow("half_basal_exercise_target", half_basal_exercise_target.toString()))
        add(KeyValueRow("maxCOB", maxCOB.toString()))
        add(KeyValueRow("skip_neutral_temps", skip_neutral_temps.toString()))
        add(KeyValueRow("remainingCarbsCap", remainingCarbsCap.toString()))
        add(KeyValueRow("enableUAM", enableUAM.toString()))
        add(KeyValueRow("A52_risk_enable", A52_risk_enable.toString()))
        add(KeyValueRow("SMBInterval", SMBInterval.toString()))
        add(KeyValueRow("enableSMB_with_COB", enableSMB_with_COB.toString()))
        add(KeyValueRow("enableSMB_with_temptarget", enableSMB_with_temptarget.toString()))
        add(KeyValueRow("allowSMB_with_high_temptarget", allowSMB_with_high_temptarget.toString()))
        add(KeyValueRow("enableSMB_always", enableSMB_always.toString()))
        add(KeyValueRow("enableSMB_after_carbs", enableSMB_after_carbs.toString()))
        add(KeyValueRow("maxSMBBasalMinutes", maxSMBBasalMinutes.toString()))
        add(KeyValueRow("maxUAMSMBBasalMinutes", maxUAMSMBBasalMinutes.toString()))
        add(KeyValueRow("bolus_increment", bolus_increment.toString()))
        add(KeyValueRow("carbsReqThreshold", carbsReqThreshold.toString()))
        add(KeyValueRow("current_basal", current_basal.toString()))
        add(KeyValueRow("temptargetSet", temptargetSet.toString()))
        add(KeyValueRow("autosens_max", autosens_max.toString()))
        add(KeyValueRow("out_units", out_units))
        lgsThreshold?.let { add(KeyValueRow("lgsThreshold", it.toString())) }
        // AutoISF-specific
        add(KeyValueRow("variable_sens", variable_sens.toString()))
        add(KeyValueRow("autoISF_version", autoISF_version))
        add(KeyValueRow("enable_autoISF", enable_autoISF.toString()))
        add(KeyValueRow("autoISF_max", autoISF_max.toString()))
        add(KeyValueRow("autoISF_min", autoISF_min.toString()))
        add(KeyValueRow("bgAccel_ISF_weight", bgAccel_ISF_weight.toString()))
        add(KeyValueRow("bgBrake_ISF_weight", bgBrake_ISF_weight.toString()))
        add(KeyValueRow("pp_ISF_weight", pp_ISF_weight.toString()))
        add(KeyValueRow("lower_ISFrange_weight", lower_ISFrange_weight.toString()))
        add(KeyValueRow("higher_ISFrange_weight", higher_ISFrange_weight.toString()))
        add(KeyValueRow("dura_ISF_weight", dura_ISF_weight.toString()))
        add(KeyValueRow("smb_delivery_ratio", smb_delivery_ratio.toString()))
        add(KeyValueRow("smb_delivery_ratio_min", smb_delivery_ratio_min.toString()))
        add(KeyValueRow("smb_delivery_ratio_max", smb_delivery_ratio_max.toString()))
        add(KeyValueRow("smb_delivery_ratio_bg_range", smb_delivery_ratio_bg_range.toString()))
        add(KeyValueRow("smb_max_range_extension", smb_max_range_extension.toString()))
        add(KeyValueRow("enableSMB_EvenOn_OddOff_always", enableSMB_EvenOn_OddOff_always.toString()))
        add(KeyValueRow("iob_threshold_percent", iob_threshold_percent.toString()))
        add(KeyValueRow("profile_percentage", profile_percentage.toString()))
    }

    private fun MealData.toRows(): List<KeyValueRow> = buildList {
        add(KeyValueRow("carbs", carbs.toString()))
        add(KeyValueRow("mealCOB", mealCOB.toString()))
        add(KeyValueRow("slopeFromMaxDeviation", slopeFromMaxDeviation.toString()))
        add(KeyValueRow("slopeFromMinDeviation", slopeFromMinDeviation.toString()))
        add(KeyValueRow("lastBolusTime", if (lastBolusTime > 0) dateUtil.dateAndTimeString(lastBolusTime) else "0"))
        add(KeyValueRow("lastCarbTime", if (lastCarbTime > 0) dateUtil.dateAndTimeString(lastCarbTime) else "0"))
        add(KeyValueRow("usedMinCarbsImpact", usedMinCarbsImpact.toString()))
    }

    private fun AutosensResult.toRows(): List<KeyValueRow> = buildList {
        add(KeyValueRow("ratio", ratio.toString()))
        add(KeyValueRow("carbsAbsorbed", carbsAbsorbed.toString()))
        add(KeyValueRow("sensResult", sensResult))
        add(KeyValueRow("pastSensitivity", pastSensitivity))
        add(KeyValueRow("ratioLimit", ratioLimit))
        add(KeyValueRow("ratioFromTdd", ratioFromTdd.toString()))
        add(KeyValueRow("ratioFromCarbs", ratioFromCarbs.toString()))
    }

    private fun RT.toRows(): List<KeyValueRow> = buildList {
        add(KeyValueRow("algorithm", algorithm.name))
        add(KeyValueRow("runningDynamicIsf", runningDynamicIsf.toString()))
        timestamp?.let { add(KeyValueRow("timestamp", dateUtil.dateAndTimeString(it))) }
        add(KeyValueRow("temp", temp))
        bg?.let { add(KeyValueRow("bg", it.toString())) }
        tick?.let { add(KeyValueRow("tick", it)) }
        eventualBG?.let { add(KeyValueRow("eventualBG", it.toString())) }
        targetBG?.let { add(KeyValueRow("targetBG", it.toString())) }
        snoozeBG?.let { add(KeyValueRow("snoozeBG", it.toString())) }
        insulinReq?.let { add(KeyValueRow("insulinReq", it.toString())) }
        carbsReq?.let { add(KeyValueRow("carbsReq", it.toString())) }
        carbsReqWithin?.let { add(KeyValueRow("carbsReqWithin", it.toString())) }
        units?.let { add(KeyValueRow("units", it.toString())) }
        deliverAt?.let { add(KeyValueRow("deliverAt", dateUtil.dateAndTimeString(it))) }
        sensitivityRatio?.let { add(KeyValueRow("sensitivityRatio", it.toString())) }
        val reasonStr = reason.toString()
        if (reasonStr.isNotEmpty()) add(KeyValueRow("reason", reasonStr))
        duration?.let { add(KeyValueRow("duration", it.toString())) }
        rate?.let { add(KeyValueRow("rate", it.toString())) }
        COB?.let { add(KeyValueRow("COB", it.toString())) }
        IOB?.let { add(KeyValueRow("IOB", it.toString())) }
        variable_sens?.let { add(KeyValueRow("variable_sens", it.toString())) }
        isfMgdlForCarbs?.let { add(KeyValueRow("isfMgdlForCarbs", it.toString())) }
    }
}
