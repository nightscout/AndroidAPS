package app.aaps.core.data.pump.defs

@Suppress("unused")
/**
 * Description of pump capabilities
 */
enum class PumpType(
    val description: String,
    private val manufacturer: ManufacturerType? = null,
    private val model: String = "NONE",
    private val bolusSize: Double = 0.0,
    private val specialBolusSize: DoseStepSize? = null,
    private val extendedBolusSettings: DoseSettings? = null,
    private val pumpTempBasalType: PumpTempBasalType? = null,
    private val tbrSettings: DoseSettings? = null,
    private val specialBasalDurations: Array<Capability> = arrayOf(Capability.BasalRate_Duration15minAllowed, Capability.BasalRate_Duration30minAllowed),
    private val baseBasalMinValue: Double = 0.01,
    private val baseBasalMaxValue: Double? = null,
    private val baseBasalStep: Double = 1.0,
    private val baseBasalSpecialSteps: DoseStepSize? = null,
    private val pumpCapability: PumpCapability? = null,
    val hasCustomUnreachableAlertCheck: Boolean = false,
    private val isPatchPump: Boolean = false,
    private val maxReservoirReading: Int = 50,
    val supportBatteryLevel: Boolean = true,
    val useHardwareLink: Boolean = false,
    val source: Source = Source.VirtualPump,
    private val parent: PumpType? = null
) {

    GENERIC_AAPS(
        description = "Generic AAPS",
        manufacturer = ManufacturerType.AAPS,
        model = "VirtualPump",
        bolusSize = 0.1,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 8 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Percent,
        tbrSettings = DoseSettings(10.0, 30, 24 * 60, 0.0, 500.0),
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration15minAllowed, Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration15minAllowed, Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration15minAllowed, Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration15minAllowed, Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration15minAllowed, Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration15minAllowed, Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration15minAllowed, Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration15minAllowed, Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration15minAllowed, Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration15minAllowed, Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration15minAllowed, Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration30minAllowed),
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
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration30minAllowed),
        baseBasalMinValue = 0.05,
        baseBasalMaxValue = 15.0,
        baseBasalStep = 0.05,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.EopatchCapabilities,
        isPatchPump = true,
        maxReservoirReading = 50,
        source = Source.EOPatch2
    ),

    //Medtrum Nano Pump
    MEDTRUM_NANO(
        description = "Medtrum Nano",
        manufacturer = ManufacturerType.Medtrum,
        model = "Nano",
        bolusSize = 0.05,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 8 * 60, 0.05, 30.0),
        pumpTempBasalType = PumpTempBasalType.Absolute,
        tbrSettings = DoseSettings(0.05, 30, 12 * 60, 0.0, 25.0),
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration30minAllowed),
        baseBasalMinValue = 0.05,
        baseBasalMaxValue = 25.0,
        baseBasalStep = 0.05,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.MedtrumCapabilities,
        isPatchPump = true,
        maxReservoirReading = 200,
        source = Source.Medtrum
    ),
    MEDTRUM_300U(
        description = "Medtrum 300U",
        manufacturer = ManufacturerType.Medtrum,
        model = "300U",
        bolusSize = 0.05,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 8 * 60, 0.05, 30.0),
        pumpTempBasalType = PumpTempBasalType.Absolute,
        tbrSettings = DoseSettings(0.05, 30, 12 * 60, 0.0, 30.0),
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration30minAllowed),
        baseBasalMinValue = 0.05,
        baseBasalMaxValue = 30.0,
        baseBasalStep = 0.05,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.MedtrumCapabilities,
        isPatchPump = true,
        maxReservoirReading = 300,
        source = Source.Medtrum
    ),
    MEDTRUM_UNTESTED(
        description = "Medtrum untested",
        model = "untested",
        parent = MEDTRUM_NANO
    ),
    EQUIL(
        description = "Equil",
        manufacturer = ManufacturerType.Equil,
        model = "Equil",
        bolusSize = 0.05,
        specialBolusSize = null,
        extendedBolusSettings = DoseSettings(0.05, 30, 5 * 60, 0.05),
        pumpTempBasalType = PumpTempBasalType.Absolute,
        tbrSettings = DoseSettings(0.05, 30, 24 * 60, 0.0, 35.0),
        specialBasalDurations = arrayOf(Capability.BasalRate_Duration30minAllowed),
        baseBasalMinValue = 0.05,
        baseBasalMaxValue = 3.0,
        baseBasalStep = 0.01,
        baseBasalSpecialSteps = null,
        pumpCapability = PumpCapability.DiaconnCapabilities,
        source = Source.EQuil,
        useHardwareLink = true,
    ),
    APEX_TRUCARE_III(
        description = "APEX TruCare III",
        manufacturer = ManufacturerType.Apex,
        model = "TruCare III",
        extendedBolusSettings = DoseSettings(0.025, 15, 24 * 60, 0.025),
        tbrSettings = DoseSettings(0.025, 15, 24 * 60, 0.0),
        pumpTempBasalType = PumpTempBasalType.Absolute,
        bolusSize = 0.025,
        baseBasalMinValue = 0.025,
        baseBasalStep = 0.025,
        baseBasalSpecialSteps = DoseStepSize.Apex,
        specialBolusSize = DoseStepSize.Apex,
        pumpCapability = PumpCapability.ApexCapabilities,
        source = Source.ApexTruCareIII,
        useHardwareLink = true,
    );

    fun manufacturer() = parent?.manufacturer ?: manufacturer ?: throw IllegalStateException()
    fun model() = parent?.model ?: model
    fun bolusSize() = parent?.bolusSize ?: bolusSize
    fun specialBolusSize() = parent?.specialBolusSize ?: specialBolusSize
    fun extendedBolusSettings() = parent?.extendedBolusSettings ?: extendedBolusSettings
    fun pumpTempBasalType() = parent?.pumpTempBasalType ?: pumpTempBasalType
    fun tbrSettings() = parent?.tbrSettings ?: tbrSettings
    fun specialBasalDurations() = parent?.specialBasalDurations ?: specialBasalDurations
    fun baseBasalMinValue() = parent?.baseBasalMinValue ?: baseBasalMinValue
    fun baseBasalMaxValue() = parent?.baseBasalMaxValue ?: baseBasalMaxValue
    fun baseBasalStep() = parent?.baseBasalStep ?: baseBasalStep
    fun baseBasalSpecialSteps() = parent?.baseBasalSpecialSteps ?: baseBasalSpecialSteps
    fun pumpCapability() = parent?.pumpCapability ?: pumpCapability
    fun isPatchPump() = parent?.isPatchPump ?: isPatchPump
    fun maxReservoirReading() = parent?.maxReservoirReading ?: maxReservoirReading

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
        Medtrum,
        MDI,
        VirtualPump,
        Unknown,
        EQuil,
        ApexTruCareIII
    }

    companion object {

        fun getByDescription(desc: String): PumpType =
            entries.firstOrNull { it.description == desc } ?: GENERIC_AAPS

        fun fromString(name: String?) = entries.firstOrNull { it.name == name }
    }
}