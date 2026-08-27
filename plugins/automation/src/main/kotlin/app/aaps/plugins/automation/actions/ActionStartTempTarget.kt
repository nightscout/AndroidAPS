package app.aaps.plugins.automation.actions

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.comment
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.icons.IcTtHigh
import app.aaps.core.ui.extensions.friendlyDescription
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientInt
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.ComparatorExists
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputTempTarget
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerDeps
import app.aaps.plugins.automation.triggers.TriggerTempTarget
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Provider
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class ActionStartTempTarget(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val activePlugin: ActivePlugin,
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil,
    private val profileUtil: ProfileUtil,
    // Only to build the Trigger precondition below.
    private val triggerDeps: TriggerDeps
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    var value = InputTempTarget(profileFunction)
    var duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)

    override var precondition: Trigger? = TriggerTempTarget(triggerDeps, ComparatorExists.Compare.NOT_EXISTS)

    override fun friendlyName(): Int = R.string.starttemptarget
    override fun shortDescription(): String = rh.gs(R.string.starttemptarget) + ": " + tt().friendlyDescription(value.units, rh, profileUtil)
    override fun composeIcon() = IcTtHigh
    override fun elementType() = ElementType.TEMP_TARGET_MANAGEMENT

    override suspend fun doAction(): PumpEnactResult =
        try {
            persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                temporaryTarget = tt(), action = app.aaps.core.data.ue.Action.TT,
                source = Sources.Automation,
                note = title,
                listValues = listOfNotNull(
                    ValueWithUnit.TETTReason(TT.Reason.AUTOMATION),
                    ValueWithUnit.Mgdl(tt().lowTarget),
                    ValueWithUnit.Mgdl(tt().highTarget).takeIf { tt().lowTarget != tt().highTarget },
                    ValueWithUnit.Minute(tt().duration.milliseconds.inWholeMinutes.toInt())
                )
            )
            pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)
        } catch (_: Exception) {
            pumpEnactResultProvider.get().success(false).comment(app.aaps.core.ui.R.string.error)
        }

    override fun hasDialog(): Boolean {
        return true
    }

    override fun toJSON(): String {
        val data = buildJsonObject {
            put("value", value.value)
            put("units", value.units.asText)
            put("durationInMinutes", duration.getMinutes())
        }
        return buildJsonObject {
            put("type", this@ActionStartTempTarget.javaClass.simpleName)
            put("data", data)
        }.toString()
    }

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        value.units = GlucoseUnit.fromText(o.lenientString("units", GlucoseUnit.MGDL.asText))
        value.value = o.lenientDouble("value")
        duration.setMinutes(o.lenientInt("durationInMinutes"))
        return this
    }

    private fun tt() = TT(
        timestamp = dateUtil.now(),
        duration = duration.getMinutes().toLong().minutes.inWholeMilliseconds,
        reason = TT.Reason.AUTOMATION,
        lowTarget = profileUtil.convertToMgdl(value.value, value.units),
        highTarget = profileUtil.convertToMgdl(value.value, value.units)
    )

    override fun isValid(): Boolean =
        if (value.units == GlucoseUnit.MMOL) { // mmol
            value.value in Constants.TT_RANGE_MMOL && duration.value > 0
        } else { // mg/dL
            value.value in Constants.TT_RANGE_MGDL && duration.value > 0
        }
}