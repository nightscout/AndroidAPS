package app.aaps.pump.medtronic.defs

import java.util.*

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 *
 * Author: Andy {andy.rozman@gmail.com}
 */
enum class PumpBolusType {

    None(0, "NONE"),  //
    Normal(1, "BOLUS_STANDARD"),  //
    Audio(2, "BOLUS_AUDIO"),  //
    Extended(3, "BOLUS_SQUARE", "AMOUNT_SQUARE=%s;DURATION=%s"),  //
    Multiwave(4, "BOLUS_MULTIWAVE", "AMOUNT=%s;AMOUNT_SQUARE=%s;DURATION=%s");

    companion object {

        var codeMapping = HashMap<Int, PumpBolusType?>()

        fun getByCode(code: Int): PumpBolusType? {
            return if (codeMapping.containsKey(code)) {
                codeMapping[code]
            } else {
                None
            }
        }

        init {
            for (pbt in PumpBolusType.entries) {
                codeMapping[pbt.code] = pbt
            }
        }
    }

    var code: Int
    var i18nKey: String
    var valueTemplate: String? = null

    constructor(code: Int, i18nKey: String) {
        this.code = code
        this.i18nKey = i18nKey
    }

    constructor(code: Int, i18nKey: String, valueTemplate: String?) {
        this.code = code
        this.i18nKey = i18nKey
        this.valueTemplate = valueTemplate
    }

    //override val name: String
}