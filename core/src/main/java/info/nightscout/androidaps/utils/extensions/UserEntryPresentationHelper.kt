package info.nightscout.androidaps.utils.extensions

import dagger.Reusable
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.Translator
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.ColorGroup
import info.nightscout.androidaps.database.entities.UserEntry.Units
import info.nightscout.androidaps.database.entities.XXXValueWithUnit
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

@Reusable
class UserEntryPresentationHelper @Inject constructor(
    private val translator: Translator,
    private val profileFunction: ProfileFunction,
    private val resourceHelper: ResourceHelper,
    private val dateUtil: DateUtil
) {

    fun colorId(colorGroup: ColorGroup): Int = when (colorGroup) {
        ColorGroup.InsulinTreatment -> R.color.basal
        ColorGroup.CarbTreatment -> R.color.carbs
        ColorGroup.TT -> R.color.tempTargetConfirmation
        ColorGroup.Profile -> R.color.white
        ColorGroup.Loop -> R.color.loopClosed
        ColorGroup.Careportal -> R.color.high
        ColorGroup.Pump -> R.color.iob
        ColorGroup.Aaps -> R.color.defaulttext
        else                        -> R.color.defaulttext
    }

    fun listToPresentationString(list: List<XXXValueWithUnit>) =
        list.joinToString(separator = " ", transform = this::toPresentationString)

    private fun toPresentationString(valueWithUnit: XXXValueWithUnit): String = when (valueWithUnit) {
        is XXXValueWithUnit.Gram -> "${valueWithUnit.value} ${translator.translate(Units.G)}"
        is XXXValueWithUnit.Hour -> "${valueWithUnit.value} ${translator.translate(Units.H)}"
        is XXXValueWithUnit.Minute -> "${valueWithUnit.value} ${translator.translate(Units.G)}"
        is XXXValueWithUnit.Percent -> "${valueWithUnit.value} ${translator.translate(Units.Percent)}"
        is XXXValueWithUnit.Insulin -> DecimalFormatter.to2Decimal(valueWithUnit.value) + translator.translate(UserEntry.Units.U)
        is XXXValueWithUnit.UnitPerHour -> DecimalFormatter.to2Decimal(valueWithUnit.value) + translator.translate(UserEntry.Units.U_H)
        is XXXValueWithUnit.SimpleInt -> valueWithUnit.value.toString()
        is XXXValueWithUnit.SimpleString -> valueWithUnit.value
        is XXXValueWithUnit.StringResource -> resourceHelper.gs(valueWithUnit.value, valueWithUnit.params.map(this::toPresentationString))
        is XXXValueWithUnit.TherapyEventMeterType -> translator.translate(valueWithUnit.value)
        is XXXValueWithUnit.TherapyEventTTReason -> translator.translate(valueWithUnit.value)
        is XXXValueWithUnit.TherapyEventType -> translator.translate(valueWithUnit.value)
        is XXXValueWithUnit.Timestamp -> dateUtil.dateAndTimeAndSecondsString(valueWithUnit.value)

        is XXXValueWithUnit.Mgdl -> {
            if (profileFunction.getUnits() == Constants.MGDL) DecimalFormatter.to0Decimal(valueWithUnit.value) + translator.translate(Units.Mg_Dl)
            else DecimalFormatter.to1Decimal(valueWithUnit.value / Constants.MMOLL_TO_MGDL) + translator.translate(Units.Mmol_L)
        }

        is XXXValueWithUnit.Mmoll -> {
            if (profileFunction.getUnits() == Constants.MGDL) DecimalFormatter.to0Decimal(valueWithUnit.value) + translator.translate(Units.Mmol_L)
            else DecimalFormatter.to1Decimal(valueWithUnit.value * Constants.MMOLL_TO_MGDL) + translator.translate(Units.Mg_Dl)
        }

        XXXValueWithUnit.UNKNOWN -> ""
    }
}