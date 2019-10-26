package info.nightscout.androidaps.plugins.pump.common.defs;


import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.pump.common.data.DoseSettings;
import info.nightscout.androidaps.utils.Round;


/**
 * Created by andy on 02/05/2018.
 * <p>
 * Most of this defintions is intended for VirtualPump only, but they can be used by other plugins.
 */

public enum PumpType {

    GenericAAPS("Generic AAPS", ManufacturerType.AndroidAPS, "VirutalPump", 0.1d, null, //
            new DoseSettings(0.05d, 30, 8 * 60, 0.05d), //
            PumpTempBasalType.Percent, //
            new DoseSettings(10, 30, 24 * 60, 0d, 500d), PumpCapability.BasalRate_Duration15and30minAllowed, //
            0.01d, 0.01d, null, PumpCapability.VirtualPumpCapabilities), //

    // Cellnovo

    Cellnovo1("Cellnovo", ManufacturerType.Cellnovo, "Cellnovo", 0.05d, null, //
            new DoseSettings(0.05d, 30, 24 * 60, 1d, null),
            PumpTempBasalType.Percent,
            new DoseSettings(5, 30, 24 * 60, 0d, 200d), PumpCapability.BasalRate_Duration30minAllowed, //
            0.05d, 0.05d, null, PumpCapability.VirtualPumpCapabilities), //

    // Accu-Chek

    AccuChekCombo("Accu-Chek Combo", ManufacturerType.Roche, "Combo", 0.1d, null, //
            new DoseSettings(0.1d, 15, 12 * 60, 0.1d), //
            PumpTempBasalType.Percent,
            new DoseSettings(10, 15, 12 * 60, 0d, 500d), PumpCapability.BasalRate_Duration15and30minAllowed, //
            0.01d, 0.01d, DoseStepSize.ComboBasal, PumpCapability.ComboCapabilities), //

    AccuChekSpirit("Accu-Chek Spirit", ManufacturerType.Roche, "Spirit", 0.1d, null, //
            new DoseSettings(0.1d, 15, 12 * 60, 0.1d), //
            PumpTempBasalType.Percent,
            new DoseSettings(10, 15, 12 * 60, 0d, 500d), PumpCapability.BasalRate_Duration15and30minAllowed, //
            0.01d, 0.1d, null, PumpCapability.VirtualPumpCapabilities), //

    AccuChekInsight("Accu-Chek Insight", ManufacturerType.Roche, "Insight", 0.05d, DoseStepSize.InsightBolus, //
            new DoseSettings(0.05d, 15, 24 * 60, 0.05d), //
            PumpTempBasalType.Percent,
            new DoseSettings(10, 15, 24 * 60, 0d, 250d), PumpCapability.BasalRate_Duration15and30minAllowed, //
            0.02d, 0.01d, null, PumpCapability.InsightCapabilities), //

    AccuChekInsightBluetooth("Accu-Chek Insight", ManufacturerType.Roche, "Insight", 0.01d, null, //
            new DoseSettings(0.01d, 15, 24 * 60, 0.05d), //
            PumpTempBasalType.Percent,
            new DoseSettings(10, 15, 24 * 60, 0d, 250d), PumpCapability.BasalRate_Duration15and30minAllowed, //
            0.02d, 0.01d, DoseStepSize.InsightBolus, PumpCapability.InsightCapabilities), //

    // Animas
    AnimasVibe("Animas Vibe", ManufacturerType.Animas, "Vibe", 0.05d, null, // AnimasBolus?
            new DoseSettings(0.05d, 30, 12 * 60, 0.05d), //
            PumpTempBasalType.Percent, //
            new DoseSettings(10, 30, 24 * 60, 0d, 300d), PumpCapability.BasalRate_Duration30minAllowed, //
            0.025d, 5d, 0d, null, PumpCapability.VirtualPumpCapabilities), //

    AnimasPing("Animas Ping", "Ping", AnimasVibe),

    // Dana
    DanaR("DanaR", ManufacturerType.Sooil, "DanaR", 0.05d, null, //
            new DoseSettings(0.05d, 30, 8 * 60, 0.05d), //
            PumpTempBasalType.Percent, //
            new DoseSettings(10d, 60, 24 * 60, 0d, 200d), PumpCapability.BasalRate_Duration15and30minNotAllowed, //
            0.04d, 0.01d, null, PumpCapability.DanaCapabilities),

    DanaRKorean("DanaR Korean", ManufacturerType.Sooil, "DanaRKorean", 0.05d, null, //
            new DoseSettings(0.05d, 30, 8 * 60, 0.05d), //
            PumpTempBasalType.Percent, //
            new DoseSettings(10d, 60, 24 * 60, 0d, 200d), PumpCapability.BasalRate_Duration15and30minNotAllowed, //
            0.1d, 0.01d, null, PumpCapability.DanaCapabilities),

    DanaRS("DanaRS", ManufacturerType.Sooil, "DanaRS", 0.05d, null, //
            new DoseSettings(0.05d, 30, 8 * 60, 0.05d), //
            PumpTempBasalType.Percent, //
            new DoseSettings(10d, 60, 24 * 60, 0d, 200d), PumpCapability.BasalRate_Duration15and30minAllowed, //
            0.04d, 0.01d, null, PumpCapability.DanaWithHistoryCapabilities),

    DanaRv2("DanaRv2", "DanaRv2", DanaRS),


    // Insulet
    Insulet_Omnipod("Insulet Omnipod", ManufacturerType.Insulet, "Omnipod", 0.05d, null, //
            new DoseSettings(0.05d, 30, 8 * 60, 0.05d), //
            PumpTempBasalType.Absolute, //
            new DoseSettings(0.05d, 30, 12 * 60, 0d, 30.0d), PumpCapability.BasalRate_Duration30minAllowed, // cannot exceed max basal rate 30u/hr
            0.05d, 0.05d, null, PumpCapability.VirtualPumpCapabilities),

    // Medtronic
    Medtronic_512_712("Medtronic 512/712", ManufacturerType.Medtronic, "512/712", 0.1d, null, //
            new DoseSettings(0.05d, 30, 8 * 60, 0.05d), //
            PumpTempBasalType.Absolute, //
            new DoseSettings(0.05d, 30, 24 * 60, 0d, 35d), PumpCapability.BasalRate_Duration30minAllowed, //
            0.05d, 0.05d, null, PumpCapability.MedtronicCapabilities), //

    Medtronic_515_715("Medtronic 515/715", "515/715", Medtronic_512_712),
    Medtronic_522_722("Medtronic 522/722", "522/722", Medtronic_512_712),

    Medtronic_523_723_Revel("Medtronic 523/723 (Revel)", ManufacturerType.Medtronic, "523/723 (Revel)", 0.05d, null, //
            new DoseSettings(0.05d, 30, 8 * 60, 0.05d), //
            PumpTempBasalType.Absolute, //
            new DoseSettings(0.05d, 30, 24 * 60, 0d, 35d), PumpCapability.BasalRate_Duration30minAllowed, //
            0.025d, 0.025d, DoseStepSize.MedtronicVeoBasal, PumpCapability.MedtronicCapabilities), //

    Medtronic_554_754_Veo("Medtronic 554/754 (Veo)", "554/754 (Veo)", Medtronic_523_723_Revel), // TODO

    Medtronic_640G("Medtronic 640G", ManufacturerType.Medtronic, "640G", 0.025d, null, //
            new DoseSettings(0.05d, 30, 8 * 60, 0.05d), //
            PumpTempBasalType.Absolute, //
            new DoseSettings(0.05d, 30, 24 * 60, 0d, 35d), PumpCapability.BasalRate_Duration30minAllowed, //
            0.025d, 0.025d, DoseStepSize.MedtronicVeoBasal, PumpCapability.VirtualPumpCapabilities), //

    // Tandem
    TandemTSlim("Tandem t:slim", ManufacturerType.Tandem, "t:slim", 0.01d, null, //
            new DoseSettings(0.01d, 15, 8 * 60, 0.4d),
            PumpTempBasalType.Percent,
            new DoseSettings(1, 15, 8 * 60, 0d, 250d), PumpCapability.BasalRate_Duration15and30minAllowed, //
            0.1d, 0.001d, null, PumpCapability.VirtualPumpCapabilities),

    TandemTFlex("Tandem t:flex", "t:flex", TandemTSlim), //
    TandemTSlimG4("Tandem t:slim G4", "t:slim G4", TandemTSlim), //
    TandemTSlimX2("Tandem t:slim X2", "t:slim X2", TandemTSlim), //

    // MDI
    MDI("MDI", ManufacturerType.AndroidAPS, "MDI");


    private String description;
    private ManufacturerType manufacturer;
    private String model;
    private double bolusSize;
    private DoseStepSize specialBolusSize;
    private DoseSettings extendedBolusSettings;
    private PumpTempBasalType pumpTempBasalType;
    private DoseSettings tbrSettings;
    private PumpCapability specialBasalDurations;
    private double baseBasalMinValue; //
    private Double baseBasalMaxValue;
    private double baseBasalStep; //
    private DoseStepSize baseBasalSpecialSteps; //
    private PumpCapability pumpCapability;

    private PumpType parent;
    private static Map<String, PumpType> mapByDescription;

    static {
        mapByDescription = new HashMap<>();

        for (PumpType pumpType : values()) {
            mapByDescription.put(pumpType.getDescription(), pumpType);
        }
    }


    PumpType(String description, String model, PumpType parent) {
        this.description = description;
        this.parent = parent;
        this.model = model;
    }


    PumpType(String description, ManufacturerType manufacturer, String model) {
        this.description = description;
        this.manufacturer = manufacturer;
        this.model = model;
    }


    PumpType(String description, String model, PumpType parent, PumpCapability pumpCapability) {
        this.description = description;
        this.parent = parent;
        this.pumpCapability = pumpCapability;
        parent.model = model;
    }

    PumpType(String description, ManufacturerType manufacturer, String model, double bolusSize, DoseStepSize specialBolusSize, //
             DoseSettings extendedBolusSettings, //
             PumpTempBasalType pumpTempBasalType, DoseSettings tbrSettings, PumpCapability specialBasalDurations, //
             double baseBasalMinValue, double baseBasalStep, DoseStepSize baseBasalSpecialSteps, PumpCapability pumpCapability) {
        this(description, manufacturer, model, bolusSize, specialBolusSize, extendedBolusSettings, pumpTempBasalType, tbrSettings, specialBasalDurations, baseBasalMinValue, null, baseBasalStep, baseBasalSpecialSteps, pumpCapability);
    }

    PumpType(String description, ManufacturerType manufacturer, String model, double bolusSize, DoseStepSize specialBolusSize, //
             DoseSettings extendedBolusSettings, //
             PumpTempBasalType pumpTempBasalType, DoseSettings tbrSettings, PumpCapability specialBasalDurations, //
             double baseBasalMinValue, Double baseBasalMaxValue, double baseBasalStep, DoseStepSize baseBasalSpecialSteps, PumpCapability pumpCapability) {
        this.description = description;
        this.manufacturer = manufacturer;
        this.model = model;
        this.bolusSize = bolusSize;
        this.specialBolusSize = specialBolusSize;
        this.extendedBolusSettings = extendedBolusSettings;
        this.pumpTempBasalType = pumpTempBasalType;
        this.tbrSettings = tbrSettings;
        this.specialBasalDurations = specialBasalDurations;
        this.baseBasalMinValue = baseBasalMinValue;
        this.baseBasalMaxValue = baseBasalMaxValue;
        this.baseBasalStep = baseBasalStep;
        this.baseBasalSpecialSteps = baseBasalSpecialSteps;
        this.pumpCapability = pumpCapability;
    }


    public String getDescription() {
        return description;
    }

    public ManufacturerType getManufacturer() {
        return isParentSet() ? parent.manufacturer : manufacturer;
    }

    public String getModel() {
        return isParentSet() ? parent.model : model;
    }

    public PumpCapability getPumpCapability() {

        if (isParentSet())
            return this.pumpCapability == null ? parent.pumpCapability : pumpCapability;
        else
            return this.pumpCapability;
    }

    public double getBolusSize() {
        return isParentSet() ? parent.bolusSize : bolusSize;
    }


    public DoseStepSize getSpecialBolusSize() {
        return isParentSet() ? parent.specialBolusSize : specialBolusSize;
    }


    public DoseSettings getExtendedBolusSettings() {
        return isParentSet() ? parent.extendedBolusSettings : extendedBolusSettings;
    }


    public PumpTempBasalType getPumpTempBasalType() {
        return isParentSet() ? parent.pumpTempBasalType : pumpTempBasalType;
    }


    public DoseSettings getTbrSettings() {
        return isParentSet() ? parent.tbrSettings : tbrSettings;
    }


    public double getBaseBasalMinValue() {
        return isParentSet() ? parent.baseBasalMinValue : baseBasalMinValue;
    }


    public Double getBaseBasalMaxValue() {
        return isParentSet() ? parent.baseBasalMaxValue : baseBasalMaxValue;
    }


    public double getBaseBasalStep() {
        return isParentSet() ? parent.baseBasalStep : baseBasalStep;
    }


    public DoseStepSize getBaseBasalSpecialSteps() {
        return isParentSet() ? parent.baseBasalSpecialSteps : baseBasalSpecialSteps;
    }


    public PumpType getParent() {
        return parent;
    }


    private boolean isParentSet() {
        return this.parent != null;
    }


    public static PumpType getByDescription(String desc) {
        if (mapByDescription.containsKey(desc)) {
            return mapByDescription.get(desc);
        } else {
            return PumpType.GenericAAPS;
        }
    }


    public String getFullDescription(String i18nTemplate, boolean hasExtendedBasals) {

        String unit = getPumpTempBasalType() == PumpTempBasalType.Percent ? "%" : "";

        DoseSettings eb = getExtendedBolusSettings();
        DoseSettings tbr = getTbrSettings();

        String extendedNote = hasExtendedBasals ? MainApp.gs(R.string.virtualpump_pump_def_extended_note) : "";

        return String.format(i18nTemplate, //
                getStep("" + getBolusSize(), getSpecialBolusSize()), //
                eb.getStep(), eb.getDurationStep(), eb.getMaxDuration() / 60, //
                getStep(getBaseBasalRange(), getBaseBasalSpecialSteps()), //
                tbr.getMinDose() + unit + "-" + tbr.getMaxDose() + unit, tbr.getStep() + unit,
                tbr.getDurationStep(), tbr.getMaxDuration() / 60, extendedNote);
    }


    private String getBaseBasalRange() {
        Double maxValue = getBaseBasalMaxValue();

        return maxValue == null ? "" + getBaseBasalMinValue() : getBaseBasalMinValue() + "-" + maxValue;
    }


    private String getStep(String step, DoseStepSize stepSize) {
        if (stepSize != null)
            return step + " [" + stepSize.getDescription() + "] *";
        else
            return "" + step;
    }


    public boolean hasExtendedBasals() {
        return ((getBaseBasalSpecialSteps() != null) || (getSpecialBolusSize() != null));
    }


    public PumpCapability getSpecialBasalDurations() {

        if (isParentSet()) {
            return parent.getSpecialBasalDurations();
        } else {
            return specialBasalDurations == null ? //
                    PumpCapability.BasalRate_Duration15and30minNotAllowed : specialBasalDurations;
        }
    }

    public double determineCorrectBolusSize(double bolusAmount) {
        if (bolusAmount == 0.0d) {
            return bolusAmount;
        }

        double bolusStepSize = getBolusSize();

        if (getSpecialBolusSize() != null) {
            DoseStepSize specialBolusSize = getSpecialBolusSize();
            bolusStepSize = specialBolusSize.getStepSizeForAmount(bolusAmount);
        }

        return Round.roundTo(bolusAmount, bolusStepSize);
    }


    public double determineCorrectBolusStepSize(double bolusAmount) {
        DoseStepSize specialBolusSize = getSpecialBolusSize();
        if (specialBolusSize != null)
            return specialBolusSize.getStepSizeForAmount(bolusAmount);
        return getBolusSize();
    }

    public double determineCorrectExtendedBolusSize(double bolusAmount) {
        if (bolusAmount == 0.0d) {
            return bolusAmount;
        }

        double bolusStepSize;

        if (getExtendedBolusSettings() == null) { // this should be never null
            return 0.0d;
        }

        DoseSettings extendedBolusSettings = getExtendedBolusSettings();

        bolusStepSize = extendedBolusSettings.getStep();

        if (bolusAmount > extendedBolusSettings.getMaxDose()) {
            bolusAmount = extendedBolusSettings.getMaxDose();
        }

        return Round.roundTo(bolusAmount, bolusStepSize);
    }


    public double determineCorrectBasalSize(double basalAmount) {
        if (basalAmount == 0.0d) {
            return basalAmount;
        }

        double basalStepSize;

        if (getBaseBasalSpecialSteps() == null) {
            basalStepSize = getBaseBasalStep();
        } else {
            DoseStepSize specialBolusSize = getBaseBasalSpecialSteps();

            basalStepSize = specialBolusSize.getStepSizeForAmount((double) basalAmount);
        }

        if (basalAmount > getTbrSettings().getMaxDose())
            basalAmount = getTbrSettings().getMaxDose().doubleValue();

        return Round.roundTo(basalAmount, basalStepSize);

    }
}
