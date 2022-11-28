package info.nightscout.interfaces.pump.defs

import info.nightscout.interfaces.R
import info.nightscout.interfaces.utils.Round
import info.nightscout.shared.interfaces.ResourceHelper
import kotlin.math.min

@Suppress("unused")
enum class PumpType {

    GENERIC_AAPS(
        description = "Generic AAPS",
        manufacturer = ManufacturerType.AAPS,
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
        pumpCapability = PumpCapability.VirtualPumpCapabilities
    ),

    CELLNOVO(
        description = "Cellnovo",
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
        pumpCapability = PumpCapability.VirtualPumpCapabilities
    ),

    ACCU_CHEK_COMBO(
        description = "Accu-Chek Combo",
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
        pumpCapability = PumpCapability.ComboCapabilities,
        source = Source.Combo,
        supportBatteryLevel = false
    ),
    ACCU_CHEK_SPIRIT(
        description = "Accu-Chek Spirit",
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
        pumpCapability = PumpCapability.VirtualPumpCapabilities
    ),
    ACCU_CHEK_INSIGHT_VIRTUAL(
        description = "Accu-Chek Insight",
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
        pumpCapability = PumpCapability.InsightCapabilities
    ),
    ACCU_CHEK_INSIGHT(
        description = "Accu-Chek Insight",
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
        pumpCapability = PumpCapability.InsightCapabilities,
        source = Source.Insight
    ),
    ACCU_CHEK_SOLO(
        description = "Accu-Chek Solo",
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
        pumpCapability = PumpCapability.InsightCapabilities
    ),

    ANIMAS_VIBE(
        description = "Animas Vibe",
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
        pumpCapability = PumpCapability.VirtualPumpCapabilities
    ),
    ANIMAS_PING(description = "Animas Ping", model = "Ping", parent = ANIMAS_VIBE),
    DANA_R(
        description = "DanaR",
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
        pumpCapability = PumpCapability.DanaCapabilities,
        source = Source.DanaR
    ),
    DANA_R_KOREAN(
        description = "DanaR Korean",
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
        pumpCapability = PumpCapability.DanaCapabilities,
        source = Source.DanaRC
    ),
    DANA_RS(
        description = "DanaRS",
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
        pumpCapability = PumpCapability.DanaWithHistoryCapabilities,
        source = Source.DanaRS
    ),
    DANA_RS_KOREAN(description = "DanaRSKorean", model = "DanaRSKorean", parent = DANA_RS),
    DANA_I(description = "DanaI", model = "DanaI", parent = DANA_RS, source = Source.DanaI),
    DANA_RV2(description = "DanaRv2", model = "DanaRv2", parent = DANA_RS, source = Source.DanaRv2),
    OMNIPOD_EROS(
        description = "Omnipod Eros",
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
        hasCustomUnreachableAlertCheck = true,
        isPatchPump = true,
        maxReservoirReading = 50,
        useHardwareLink = true,
        supportBatteryLevel = false,
        source = Source.OmnipodEros
    ),
    OMNIPOD_DASH(
        description = "Omnipod Dash",
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
        isPatchPump = true,
        maxReservoirReading = 50,
        pumpCapability = PumpCapability.OmnipodCapabilities,
        hasCustomUnreachableAlertCheck = false,
        supportBatteryLevel = false
    ),
    MEDTRONIC_512_712(
        description = "Medtronic 512/712",
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
        pumpCapability = PumpCapability.MedtronicCapabilities,
        source = Source.Medtronic
    ),
    MEDTRONIC_515_715(
        description = "Medtronic 515/715",
        model = "515/715",
        parent = MEDTRONIC_512_712
    ),
    MEDTRONIC_522_722(
        description = "Medtronic 522/722",
        model = "522/722",
        parent = MEDTRONIC_512_712
    ),
    MEDTRONIC_523_723_REVEL(
        description = "Medtronic 523/723 (Revel)",
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
        pumpCapability = PumpCapability.MedtronicCapabilities,
        source = Source.Medtronic
    ),
    MEDTRONIC_554_754_VEO(description = "Medtronic 554/754 (Veo)", model = "554/754 (Veo)", parent = MEDTRONIC_523_723_REVEL),
    MEDTRONIC_640G(
        description = "Medtronic 640G",
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
        pumpCapability = PumpCapability.VirtualPumpCapabilities
    ),

    TANDEM_T_SLIM(
        description = "Tandem t:slim",
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
        pumpCapability = PumpCapability.VirtualPumpCapabilities
    ),
    TANDEM_T_FLEX(description = "Tandem t:flex", model = "t:flex", parent = TANDEM_T_SLIM),
    TANDEM_T_SLIM_G4(description = "Tandem t:slim G4", model = "t:slim G4", parent = TANDEM_T_SLIM),
    TANDEM_T_SLIM_X2(description = "Tandem t:slim X2", model = "t:slim X2", parent = TANDEM_T_SLIM),

    YPSOPUMP(
        description = "YpsoPump",
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
        pumpCapability = PumpCapability.YpsomedCapabilities
    ),
    MDI(
        description = "MDI",
        manufacturer = ManufacturerType.AAPS,
        bolusSize = 0.5,
        model = "MDI",
        tbrSettings = DoseSettings(1.0, 15, 24 * 60, 0.0, 500.0),
        extendedBolusSettings = DoseSettings(0.1, 15, 12 * 60, 0.1),
        pumpCapability = PumpCapability.MDI
    ),

    // Not real pump. Used for User as a source
    USER(
        description = "USER",
        manufacturer = ManufacturerType.AAPS,
        model = "USER",
        tbrSettings = DoseSettings(1.0, 15, 24 * 60, 0.0, 500.0),
        extendedBolusSettings = DoseSettings(0.1, 15, 12 * 60, 0.1),
        pumpCapability = PumpCapability.MDI,
        source = Source.MDI
    ),

    // Not real, cached value
    CACHE(
        description = "CACHE",
        model = "CACHE",
        parent = USER
    ),

    //Diaconn Pump
    DIACONN_G8(
        description = "Diaconn G8",
        manufacturer = ManufacturerType.G2e,
        model = "DiaconnG8",
        bolusSize = 0.01,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 10, 5 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Absolute,
        tbrSettings = DoseSettings(0.01, 30, 24 * 60, 0.0, 15.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration30minAllowed,
        baseBasalMinValue = 0.05,
        baseBasalMaxValue = 3.0,
        baseBasalStep = 0.01,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.DiaconnCapabilities,
        source = Source.DiaconnG8
    ),

    //EOPatch Pump
    EOFLOW_EOPATCH2(
        description = "Eoflow Eopatch2",
        manufacturer = ManufacturerType.Eoflow,
        model = "Eopatch",
        bolusSize = 0.05,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 8 * 60, 0.05, 25.0),
        pumpTempBasalType = PumpTempBasalType.Absolute,
        tbrSettings = DoseSettings(0.05, 30, 12 * 60, 0.0, 15.0),
        specialBasalDurations = PumpCapability.BasalRate_Duration30minAllowed,
        baseBasalMinValue = 0.05,
        baseBasalMaxValue = 15.0,
        baseBasalStep = 0.05,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.EopatchCapabilities,
        isPatchPump = true,
        maxReservoirReading = 50,
        source = Source.EOPatch2
    );

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
    var isPatchPump = false
        private set
    var maxReservoirReading = 50
        private set
    var supportBatteryLevel = true
        private set
    var useHardwareLink = false
        private set
    private var parent: PumpType? = null
    val source: Source

    enum class Source {
        Dana,
        DanaR,
        DanaRC,
        DanaRv2,
        DanaRS,
        DanaI,
        DiaconnG8,
        Insight,
        Combo,
        Medtronic,
        Omnipod,
        OmnipodEros,
        OmnipodDash,
        EOPatch2,
        MDI,
        VirtualPump,
        Unknown
    }

    companion object {

        fun getByDescription(desc: String): PumpType =
            values().firstOrNull { it.description == desc } ?: GENERIC_AAPS

    }

    constructor(description: String, model: String, parent: PumpType, pumpCapability: PumpCapability? = null, source: Source? = null) {
        this.description = description
        this.parent = parent
        this.source = source ?: parent.source
        this.pumpCapability = pumpCapability
        parent.model = model
    }

    constructor(
        description: String,
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
        hasCustomUnreachableAlertCheck: Boolean = false,
        isPatchPump: Boolean = false,
        maxReservoirReading: Int = 50,
        supportBatteryLevel: Boolean = true,
        useHardwareLink: Boolean = false,
        source: Source = Source.VirtualPump
    ) {
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
        this.isPatchPump = isPatchPump
        this.maxReservoirReading = maxReservoirReading
        this.supportBatteryLevel = supportBatteryLevel
        this.useHardwareLink = useHardwareLink
        this.source = source
    }

    fun getFullDescription(i18nTemplate: String, hasExtendedBasals: Boolean, rh: ResourceHelper): String {
        val unit = if (pumpTempBasalType == PumpTempBasalType.Percent) "%" else ""
        val eb = extendedBolusSettings ?: return "INVALID"
        val tbr = tbrSettings ?: return "INVALID"
        val extendedNote = if (hasExtendedBasals) rh.gs(R.string.def_extended_note) else ""
        return String.format(
            i18nTemplate,
            getStep("" + bolusSize, specialBolusSize),
            eb.step, eb.durationStep, eb.maxDuration / 60,
            getStep(baseBasalRange(), baseBasalSpecialSteps),
            tbr.minDose.toString() + unit + "-" + tbr.maxDose + unit, tbr.step.toString() + unit,
            tbr.durationStep, tbr.maxDuration / 60, extendedNote
        )
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
        return Round.roundTo(
            min(basalAmount, tSettings.maxDose), baseBasalSpecialSteps?.getStepSizeForAmount(basalAmount)
                ?: baseBasalStep
        )
    }
}