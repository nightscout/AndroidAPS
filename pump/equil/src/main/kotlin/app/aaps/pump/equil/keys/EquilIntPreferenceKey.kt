package app.aaps.pump.equil.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.equil.R

enum class EquilIntPreferenceKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int = Int.MIN_VALUE,
    override val max: Int = Int.MAX_VALUE,
    private val titleResId: Int,
    private val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    private val entriesResIds: Map<Int, Int> = emptyMap(),
) : IntPreferenceKey {

    EquilTone(
        key = "key_equil_tone",
        defaultValue = 3,
        min = 0,
        max = 3,
        titleResId = R.string.equil_tone,
        preferenceType = PreferenceType.LIST,
        entriesResIds = mapOf(
            0 to R.string.equil_tone_mode_mute,
            1 to R.string.equil_tone_mode_tone,
            2 to R.string.equil_tone_mode_shake,
            3 to R.string.equil_tone_mode_tone_and_shake
        )
    ),

    /**
     * Bolus speed, stored as seconds per unit: the pump takes the bolus duration in the bolus command.
     * Tested on a real pump up to 12 U/min; 30 U/min ended in "Abnormal infusion stopped" (#5142).
     */
    EquilBolusSpeed(
        key = "key_equil_bolus_speed",
        defaultValue = 40,
        min = 5,
        max = 40,
        titleResId = R.string.equil_bolus_speed,
        summaryResId = R.string.equil_bolus_speed_summary,
        preferenceType = PreferenceType.LIST,
        entriesResIds = mapOf(
            40 to R.string.equil_bolus_speed_1_5,
            20 to R.string.equil_bolus_speed_3,
            10 to R.string.equil_bolus_speed_6,
            5 to R.string.equil_bolus_speed_12
        )
    ),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
    override val entries: Map<Int, TextRef> = entriesResIds.mapValues { TextRef.AndroidRes(it.value) }
}
