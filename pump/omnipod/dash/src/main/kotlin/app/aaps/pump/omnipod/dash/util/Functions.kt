package app.aaps.pump.omnipod.dash.util

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.defs.determineCorrectBasalSize
import app.aaps.pump.omnipod.dash.driver.pod.definition.BasalProgram
import kotlin.math.roundToInt

fun mapProfileToBasalProgram(profile: Profile): BasalProgram {
    val basalValues = profile.getBasalValues()
    if (basalValues.isEmpty()) {
        throw IllegalArgumentException("Basal values should contain values")
    }

    val entries: MutableList<BasalProgram.Segment> = ArrayList()

    var previousBasalValue: Profile.ProfileValue? = null

    for (basalValue in basalValues) {
        if (basalValue.timeAsSeconds >= 86_400) {
            throw IllegalArgumentException("Basal segment start time can not be greater than 86400")
        }
        if (basalValue.timeAsSeconds < 0) {
            throw IllegalArgumentException("Basal segment start time can not be less than 0")
        }
        if (basalValue.timeAsSeconds % 1_800 != 0) {
            throw IllegalArgumentException("Basal segment time should be dividable by 30 minutes")
        }

        val startSlotIndex = (basalValue.timeAsSeconds / 1800).toShort()

        if (previousBasalValue != null) {
            entries.add(
                BasalProgram.Segment(
                    (previousBasalValue.timeAsSeconds / 1800).toShort(),
                    startSlotIndex,
                    (PumpType.OMNIPOD_DASH.determineCorrectBasalSize(previousBasalValue.value) * 100).roundToInt()
                )
            )
        }

        if (entries.isEmpty() && basalValue.timeAsSeconds != 0) {
            throw IllegalArgumentException("First basal segment start time should be 0")
        }

        if (entries.isNotEmpty() && entries[entries.size - 1].endSlotIndex != startSlotIndex) {
            throw IllegalArgumentException("Illegal start time for basal segment: does not match previous previous segment's end time")
        }

        previousBasalValue = basalValue
    }

    entries.add(
        BasalProgram.Segment(
            (previousBasalValue!!.timeAsSeconds / 1800).toShort(),
            48,
            (PumpType.OMNIPOD_DASH.determineCorrectBasalSize(previousBasalValue.value) * 100).roundToInt()
        )
    )

    return BasalProgram(entries)
}
