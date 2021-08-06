package info.nightscout.androidaps.plugins.pump.common.defs

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.plugins.common.ManufacturerType
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlin.math.min

@Suppress("unused")
enum class PumpType {

    GENERIC_AAPS(description = "Generic AAPS",
        manufacturer = ManufacturerType.AndroidAPS,
        model = "VirtualPump",
        bolusSize = 0.1,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 8 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Percent,
        tbrSettings = DoseSettings(10.0, 30, 24 * 60, 0.0, 500.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration15and30minAllowed,
        baseBasalMinValue = 0.01,
        baseBasalStep = 0.01,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.VirtualPumpCapabilities),

    CELLNOVO(description = "Cellnovo",
        manufacturer = ManufacturerType.Cellnovo,
        model = "Cellnovo",
        bolusSize = 0.05,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 24 * 60, 1.0),
        pumpTempBasalType = PumpTempBasalType.Percent,
        tbrSettings = DoseSettings(5.0, 30, 24 * 60, 0.0, 200.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration30minAllowed,
        baseBasalMinValue = 0.05,
        baseBasalStep = 0.05,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.VirtualPumpCapabilities),

    ACCU_CHEK_COMBO(description = "Accu-Chek Combo",
        manufacturer = ManufacturerType.Roche,
        model = "Combo",
        bolusSize = 0.1,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.1, 15, 12 * 60, 0.1),
        pumpTempBasalType = PumpTempBasalType.Percent,
        tbrSettings = DoseSettings(10.0, 15, 12 * 60, 0.0, 500.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration15and30minAllowed,
        baseBasalMinValue = 0.01,
        baseBasalStep = 0.01,
        baseBasalSpecialSteps = DoseStepSize.ComboBasal,
        pumpCapability = PumpCapability.ComboCapabilities),
    ACCU_CHEK_SPIRIT(description = "Accu-Chek Spirit",
        manufacturer = ManufacturerType.Roche,
        model = "Spirit",
        bolusSize = 0.1,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.1, 15, 12 * 60, 0.1),
        pumpTempBasalType = PumpTempBasalType.Percent,
        tbrSettings = DoseSettings(10.0, 15, 12 * 60, 0.0, 500.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration15and30minAllowed,
        baseBasalMinValue = 0.01,
        baseBasalStep = 0.1,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.VirtualPumpCapabilities),
    ACCU_CHEK_INSIGHT_VIRTUAL(description = "Accu-Chek Insight",
        manufacturer = ManufacturerType.Roche,
        model = "Insight",
        bolusSize = 0.05,
        specialBolusSize = DoseStepSize.InsightBolus,
        extendedBolusSettings = DoseSettings(0.05, 15, 24 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Percent,
        tbrSettings = DoseSettings(10.0, 15, 24 * 60, 0.0, 250.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration15and30minAllowed,
        baseBasalMinValue = 0.02,
        baseBasalStep = 0.01,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.InsightCapabilities),
    ACCU_CHEK_INSIGHT(description = "Accu-Chek Insight",
        manufacturer = ManufacturerType.Roche,
        model = "Insight",
        bolusSize = 0.01,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.01, 15, 24 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Percent,
        tbrSettings = DoseSettings(10.0, 15, 24 * 60, 0.0, 250.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration15and30minAllowed,
        baseBasalMinValue = 0.02,
        baseBasalMaxValue = null,
        baseBasalStep = 0.01,
        baseBasalSpecialSteps = DoseStepSize.InsightBasal,
        pumpCapability = PumpCapability.InsightCapabilities),
    ACCU_CHEK_SOLO(description = "Accu-Chek Solo",
        manufacturer = ManufacturerType.Roche,
        model = "Solo",
        bolusSize = 0.01,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.01, 15, 24 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Percent,
        tbrSettings = DoseSettings(10.0, 15, 24 * 60, 0.0, 250.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration15and30minAllowed,
        baseBasalMinValue = 0.02,
        baseBasalMaxValue = null,
        baseBasalStep = 0.01,
        baseBasalSpecialSteps = DoseStepSize.InsightBolus,
        pumpCapability = PumpCapability.InsightCapabilities),

    ANIMAS_VIBE(description = "Animas Vibe",
        manufacturer = ManufacturerType.Animas,
        model = "Vibe",
        bolusSize = 0.05,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 12 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Percent,
        tbrSettings = DoseSettings(10.0, 30, 24 * 60, 0.0, 300.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration30minAllowed,
        baseBasalMinValue = 0.025,
        baseBasalMaxValue = 5.0,
        baseBasalStep = 0.0,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.VirtualPumpCapabilities),
    ANIMAS_PING(description = "Animas Ping", model = "Ping", parent = ANIMAS_VIBE),
    DANA_R(description = "DanaR",
        manufacturer = ManufacturerType.Sooil,
        model = "DanaR",
        bolusSize = 0.05,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 8 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Percent,
        tbrSettings = DoseSettings(10.0, 60, 24 * 60, 0.0, 200.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration15and30minNotAllowed,
        baseBasalMinValue = 0.04,
        baseBasalStep = 0.01,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.DanaCapabilities),
    DANA_R_KOREAN(description = "DanaR Korean",
        manufacturer = ManufacturerType.Sooil,
        model = "DanaRKorean",
        bolusSize = 0.05,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 8 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Percent,
        tbrSettings = DoseSettings(10.0, 60, 24 * 60, 0.0, 200.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration15and30minNotAllowed,
        baseBasalMinValue = 0.1,
        baseBasalStep = 0.01,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.DanaCapabilities),
    DANA_RS(description = "DanaRS",
        manufacturer = ManufacturerType.Sooil,
        model = "DanaRS",
        bolusSize = 0.05,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 8 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Percent,
        tbrSettings = DoseSettings(10.0, 60, 24 * 60, 0.0, 200.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration15and30minAllowed,
        baseBasalMinValue = 0.04,
        baseBasalStep = 0.01,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.DanaWithHistoryCapabilities),
    DANA_RS_KOREAN(description = "DanaRSKorean", model = "DanaRSKorean", parent = DANA_RS),
    DANA_I(description = "DanaI", model = "DanaI", parent = DANA_RS),
    DANA_RV2(description = "DanaRv2", model = "DanaRv2", parent = DANA_RS),
    OMNIPOD_EROS(description = "Omnipod Eros",
        manufacturer = ManufacturerType.Insulet,
        model = "Eros",
        bolusSize = 0.05,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 8 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Absolute,
        tbrSettings = DoseSettings(0.05, 30, 12 * 60, 0.0, 30.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration30minAllowed,
        baseBasalMinValue = 0.05,
        baseBasalMaxValue = null,
        baseBasalStep = 0.05,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.OmnipodCapabilities,
        hasCustomUnreachableAlertCheck = true),
    OMNIPOD_DASH(description = "Omnipod Dash",
        manufacturer = ManufacturerType.Insulet,
        model = "Dash",
        bolusSize = 0.05,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 8 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Absolute,
        tbrSettings = DoseSettings(0.05, 30, 12 * 60, 0.0, 30.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration30minAllowed,
        baseBasalMinValue = 0.05,
        baseBasalMaxValue = null,
        baseBasalStep = 0.05,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.OmnipodCapabilities,
        hasCustomUnreachableAlertCheck = false),
    MEDTRONIC_512_712(description = "Medtronic 512/712",
        manufacturer = ManufacturerType.Medtronic,
        model = "512/712",
        bolusSize = 0.1,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 8 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Absolute,
        tbrSettings = DoseSettings(0.05, 30, 24 * 60, 0.0, 35.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration30minAllowed,
        baseBasalMinValue = 0.05,
        baseBasalStep = 0.05,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.MedtronicCapabilities),
    MEDTRONIC_515_715(description = "Medtronic 515/715",
        model = "515/715",
        parent = MEDTRONIC_512_712),
    MEDTRONIC_522_722(description = "Medtronic 522/722",
        model = "522/722",
        parent = MEDTRONIC_512_712),
    MEDTRONIC_523_723_REVEL(description = "Medtronic 523/723 (Revel)",
        manufacturer = ManufacturerType.Medtronic,
        model = "523/723 (Revel)",
        bolusSize = 0.05,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 8 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Absolute,
        tbrSettings = DoseSettings(0.05, 30, 24 * 60, 0.0, 35.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration30minAllowed,
        baseBasalMinValue = 0.025,
        baseBasalStep = 0.025,
        baseBasalSpecialSteps = DoseStepSize.MedtronicVeoBasal,
        pumpCapability = PumpCapability.MedtronicCapabilities),
    MEDTRONIC_554_754_VEO(description = "Medtronic 554/754 (Veo)", model = "554/754 (Veo)", parent = MEDTRONIC_523_723_REVEL),
    MEDTRONIC_640G(description = "Medtronic 640G",
        manufacturer = ManufacturerType.Medtronic,
        model = "640G",
        bolusSize = 0.025,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 8 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Absolute,
        tbrSettings = DoseSettings(0.05, 30, 24 * 60, 0.0, 35.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration30minAllowed,
        baseBasalMinValue = 0.025,
        baseBasalStep = 0.025,
        baseBasalSpecialSteps = DoseStepSize.MedtronicVeoBasal,
        pumpCapability = PumpCapability.VirtualPumpCapabilities),

    TANDEM_T_SLIM(description = "Tandem t:slim",
        manufacturer = ManufacturerType.Tandem,
        model = "t:slim",
        bolusSize = 0.01,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.01, 15, 8 * 60, 0.4),
        pumpTempBasalType = PumpTempBasalType.Percent,
        tbrSettings = DoseSettings(1.0, 15, 8 * 60, 0.0, 250.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration15and30minAllowed,
        baseBasalMinValue = 0.1,
        baseBasalStep = 0.001,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.VirtualPumpCapabilities),
    TANDEM_T_FLEX(description = "Tandem t:flex", model = "t:flex", parent = TANDEM_T_SLIM),
    TANDEM_T_SLIM_G4(description = "Tandem t:slim G4", model = "t:slim G4", parent = TANDEM_T_SLIM),
    TANDEM_T_SLIM_X2(description = "Tandem t:slim X2", model = "t:slim X2", parent = TANDEM_T_SLIM),

    YPSOPUMP(description = "YpsoPump",
        manufacturer = ManufacturerType.Ypsomed,
        model = "Ypsopump",
        bolusSize = 0.1,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.1, 15, 12 * 60, 0.1),
        pumpTempBasalType = PumpTempBasalType.Percent,
        tbrSettings = DoseSettings(1.0, 15, 24 * 60, 0.0, 500.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration15and30minAllowed,
        baseBasalMinValue = 0.02,
        baseBasalMaxValue = 40.0,
        baseBasalStep = 0.01,
        baseBasalSpecialSteps = DoseStepSize.YpsopumpBasal,
        pumpCapability = PumpCapability.YpsomedCapabilities),
    MDI(description = "MDI",
        manufacturer = ManufacturerType.AndroidAPS,
        model = "MDI",
        tbrSettings = DoseSettings(1.0, 15, 24 * 60, 0.0, 500.0),
        extendedBolusSettings = DoseSettings(0.1, 15, 12 * 60, 0.1),
        pumpCapability = PumpCapability.MDI),

    // Not real pump. Used for User as a source
    USER(description = "USER",
        manufacturer = ManufacturerType.AndroidAPS,
        model = "USER",
        tbrSettings = DoseSettings(1.0, 15, 24 * 60, 0.0, 500.0),
        extendedBolusSettings = DoseSettings(0.1, 15, 12 * 60, 0.1),
        pumpCapability = PumpCapability.MDI),

    //Diaconn Pump
    DIACONN_G8(description = "DiaconnG8",
        manufacturer = ManufacturerType.G2e,
        model = "Diaconn G8",
        bolusSize = 0.01,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 10, 5 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Absolute,
        tbrSettings = DoseSettings(0.01, 30, 24 * 60, 0.0, 6.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration30minAllowed,
        baseBasalMinValue = 0.05,
        baseBasalMaxValue = 3.0,
        baseBasalStep = 0.01,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.DanaWithHistoryCapabilities);

    val description: String
    var manufacturer: ManufacturerType? = null
        get() = parent?.manufacturer ?: field
        private set
    var model: String = "NONE"
        get() = parent?.model ?: field
        private set
    var bolusSize = 0.0
        get() = parent?.bolusSize ?: field
    private var specialBolusSize: DoseStepSize? = null
        get() = parent?.specialBolusSize ?: field
    var extendedBolusSettings: DoseSettings? = null
        get() = parent?.extendedBolusSettings ?: field
        private set
    var pumpTempBasalType: PumpTempBasalType? = null
        get() = parent?.pumpTempBasalType ?: field
        private set
    var tbrSettings: DoseSettings? = null
        get() = parent?.tbrSettings ?: field
        private set
    var specialBasalDurations: PumpCapability? = null
        get() = parent?.specialBasalDurations ?: field
        ?: PumpCapability.BasalRate_Duration15and30minNotAllowed
        private set
    var baseBasalMinValue = 0.01
        get() = parent?.baseBasalMinValue ?: field
        private set
    private var baseBasalMaxValue: Double? = null
        get() = parent?.baseBasalMaxValue ?: field
    var baseBasalStep = 1.0
        get() = parent?.baseBasalStep ?: field
        private set
    private var baseBasalSpecialSteps: DoseStepSize? = null
        get() = parent?.baseBasalSpecialSteps ?: field
    var pumpCapability: PumpCapability? = null
        get() = parent?.pumpCapability ?: field
        private set
    var hasCustomUnreachableAlertCheck = false
        private set
    private var parent: PumpType? = null

    companion object {

        fun getByDescription(desc: String): PumpType =
            values().firstOrNull { it.description == desc } ?: GENERIC_AAPS
    }

    constructor(description: String, model: String, parent: PumpType, pumpCapability: PumpCapability? = null) {
        this.description = description
        this.parent = parent
        this.pumpCapability = pumpCapability
        parent.model = model
    }

    constructor(description: String,
                manufacturer: ManufacturerType,
                model: String,
                bolusSize: Double = 0.0,
                specialBolusSize: DoseStepSize? = null,
                extendedBolusSettings: DoseSettings,
                pumpTempBasalType: PumpTempBasalType? = null,
                tbrSettings: DoseSettings,
                specialBasalDurations: PumpCapability? = null,
                baseBasalMinValue: Double = 0.01,
                baseBasalMaxValue: Double? = null,
                baseBasalStep: Double = 1.0,
                baseBasalSpecialSteps: DoseStepSize? = null,
                pumpCapability: PumpCapability,
                hasCustomUnreachableAlertCheck: Boolean = false) {
        this.description = description
        this.manufacturer = manufacturer
        this.model = model
        this.bolusSize = bolusSize
        this.specialBolusSize = specialBolusSize
        this.extendedBolusSettings = extendedBolusSettings
        this.pumpTempBasalType = pumpTempBasalType
        this.tbrSettings = tbrSettings
        this.specialBasalDurations = specialBasalDurations
        this.baseBasalMinValue = baseBasalMinValue
        this.baseBasalMaxValue = baseBasalMaxValue
        this.baseBasalStep = baseBasalStep
        this.baseBasalSpecialSteps = baseBasalSpecialSteps
        this.pumpCapability = pumpCapability
        this.hasCustomUnreachableAlertCheck = hasCustomUnreachableAlertCheck
    }

    fun getFullDescription(i18nTemplate: String, hasExtendedBasals: Boolean, resourceHelper: ResourceHelper): String {
        val unit = if (pumpTempBasalType == PumpTempBasalType.Percent) "%" else ""
        val eb = extendedBolusSettings ?: return "INVALID"
        val tbr = tbrSettings ?: return "INVALID"
        val extendedNote = if (hasExtendedBasals) resourceHelper.gs(R.string.def_extended_note) else ""
        return String.format(i18nTemplate,
            getStep("" + bolusSize, specialBolusSize),
            eb.step, eb.durationStep, eb.maxDuration / 60,
            getStep(baseBasalRange(), baseBasalSpecialSteps),
            tbr.minDose.toString() + unit + "-" + tbr.maxDose + unit, tbr.step.toString() + unit,
            tbr.durationStep, tbr.maxDuration / 60, extendedNote)
    }

    private fun baseBasalRange(): String =
        if (baseBasalMaxValue == null) baseBasalMinValue.toString()
        else baseBasalMinValue.toString() + "-" + baseBasalMaxValue.toString()

    private fun getStep(step: String, stepSize: DoseStepSize?): String =
        if (stepSize != null) step + " [" + stepSize.description + "] *"
        else step

    fun hasExtendedBasals(): Boolean = baseBasalSpecialSteps != null || specialBolusSize != null

    fun determineCorrectBolusSize(bolusAmount: Double): Double =
        Round.roundTo(bolusAmount, specialBolusSize?.getStepSizeForAmount(bolusAmount) ?: bolusSize)

    fun determineCorrectBolusStepSize(bolusAmount: Double): Double =
        specialBolusSize?.getStepSizeForAmount(bolusAmount) ?: bolusSize

    fun determineCorrectExtendedBolusSize(bolusAmount: Double): Double {
        val ebSettings = extendedBolusSettings ?: throw IllegalStateException()
        return Round.roundTo(min(bolusAmount, ebSettings.maxDose), ebSettings.step)
    }

    fun determineCorrectBasalSize(basalAmount: Double): Double {
        val tSettings = tbrSettings ?: throw IllegalStateException()
        return Round.roundTo(min(basalAmount, tSettings.maxDose), baseBasalSpecialSteps?.getStepSizeForAmount(basalAmount)
            ?: baseBasalStep)
    }

    fun toDbPumpType(): InterfaceIDs.PumpType =
        when (this) {
            GENERIC_AAPS                -> InterfaceIDs.PumpType.GENERIC_AAPS
            CELLNOVO                    -> InterfaceIDs.PumpType.CELLNOVO
            ACCU_CHEK_COMBO             -> InterfaceIDs.PumpType.ACCU_CHEK_COMBO
            ACCU_CHEK_SPIRIT            -> InterfaceIDs.PumpType.ACCU_CHEK_SPIRIT
            ACCU_CHEK_INSIGHT_VIRTUAL -> InterfaceIDs.PumpType.ACCU_CHEK_INSIGHT
            ACCU_CHEK_INSIGHT         -> InterfaceIDs.PumpType.ACCU_CHEK_INSIGHT_BLUETOOTH
            ACCU_CHEK_SOLO            -> InterfaceIDs.PumpType.ACCU_CHEK_SOLO
            ANIMAS_VIBE                 -> InterfaceIDs.PumpType.ANIMAS_VIBE
            ANIMAS_PING                 -> InterfaceIDs.PumpType.ANIMAS_PING
            DANA_R                      -> InterfaceIDs.PumpType.DANA_R
            DANA_R_KOREAN               -> InterfaceIDs.PumpType.DANA_R_KOREAN
            DANA_RS                     -> InterfaceIDs.PumpType.DANA_RS
            DANA_RS_KOREAN              -> InterfaceIDs.PumpType.DANA_RS_KOREAN
            DANA_RV2                    -> InterfaceIDs.PumpType.DANA_RV2
            DANA_I                      -> InterfaceIDs.PumpType.DANA_I
            OMNIPOD_EROS                -> InterfaceIDs.PumpType.OMNIPOD_EROS
            OMNIPOD_DASH                -> InterfaceIDs.PumpType.OMNIPOD_DASH
            MEDTRONIC_512_712           -> InterfaceIDs.PumpType.MEDTRONIC_512_517
            MEDTRONIC_515_715           -> InterfaceIDs.PumpType.MEDTRONIC_515_715
            MEDTRONIC_522_722           -> InterfaceIDs.PumpType.MEDTRONIC_522_722
            MEDTRONIC_523_723_REVEL     -> InterfaceIDs.PumpType.MEDTRONIC_523_723_REVEL
            MEDTRONIC_554_754_VEO       -> InterfaceIDs.PumpType.MEDTRONIC_554_754_VEO
            MEDTRONIC_640G              -> InterfaceIDs.PumpType.MEDTRONIC_640G
            TANDEM_T_SLIM               -> InterfaceIDs.PumpType.TANDEM_T_SLIM
            TANDEM_T_SLIM_G4            -> InterfaceIDs.PumpType.TANDEM_T_SLIM_G4
            TANDEM_T_FLEX               -> InterfaceIDs.PumpType.TANDEM_T_FLEX
            TANDEM_T_SLIM_X2            -> InterfaceIDs.PumpType.TANDEM_T_SLIM_X2
            YPSOPUMP                    -> InterfaceIDs.PumpType.YPSOPUMP
            MDI                         -> InterfaceIDs.PumpType.MDI
            USER                        -> InterfaceIDs.PumpType.USER
            DIACONN_G8                  -> InterfaceIDs.PumpType.DIACONN_G8
        }
}