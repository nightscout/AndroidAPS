package app.aaps.plugins.source.keys

import app.aaps.core.keys.UnitType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.source.R

enum class Libre3IntKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int,
    override val max: Int,
    private val titleResId: Int,
    override val dependency: BooleanPreferenceKey? = null,
    override val unitType: UnitType = UnitType.NONE,
    override val exportable: Boolean = true,
) : IntPreferenceKey {

    /** Hours after the NFC activation before [Libre3BooleanKey.AutoPromote] switches the sensor. */
    AutoPromoteHours(
        key = "libre3_staging_auto_promote_hours",
        defaultValue = 10,
        min = 6,
        max = 24,
        titleResId = R.string.staging_auto_promote_hours_title,
        dependency = Libre3BooleanKey.AutoPromote,
        unitType = UnitType.HOURS,
    ),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
}
