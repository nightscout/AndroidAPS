package info.nightscout.androidaps.plugins.PumpCommon.defs;


import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PumpCommon.data.DoseSettings;



/**
 * Created by andy on 02/05/2018.
 *
 * Most of this defintions is intended for VirtualPump only, but they can be used by other plugins.
 */

public enum PumpType {

    GenericAAPS("Generic AAPS", 0.1f, null, //
            new DoseSettings(0.05f, 30, 8*60, 0.05f), //
            PumpTempBasalType.Percent, //
            new DoseSettings(10,30, 24*60, 0f, 500f), PumpCapability.BasalRate_Duration15and30minAllowed, //
            0.01f, 0.01f, null, PumpCapability.VirtualPumpCapabilities), //

    // Cellnovo

    Cellnovo1("Cellnovo", 0.05f, null, //
            new DoseSettings(0.05f, 30,  24*60, 1f, null),
            PumpTempBasalType.Percent,
            new DoseSettings(5,30, 24*60, 0f, 200f), PumpCapability.BasalRate_Duration30minAllowed, //
            0.05f, 0.05f, null, PumpCapability.VirtualPumpCapabilities), //

    // Accu-Chek

    AccuChekCombo("Accu-Chek Combo", 0.1f, null, //
            new DoseSettings(0.1f, 15, 12*60, 0.1f), //
            PumpTempBasalType.Percent,
            new DoseSettings(10,  15, 12*60,0f, 500f), PumpCapability.BasalRate_Duration15and30minAllowed, //
            0.01f, 0.1f, DoseStepSize.ComboBasal, PumpCapability.ComboCapabilities), //

    AccuChekSpirit("Accu-Chek Spirit", 0.1f, null, //
            new DoseSettings(0.1f, 15, 12*60, 0.1f), //
            PumpTempBasalType.Percent,
            new DoseSettings(10,  15, 12*60,0f, 500f), PumpCapability.BasalRate_Duration15and30minAllowed, //
            0.01f, 0.1f, null, PumpCapability.VirtualPumpCapabilities), //

    AccuChekInsight("Accu-Chek Insight", 0.05f, DoseStepSize.InsightBolus, //
            new DoseSettings(0.05f, 15, 24*60, 0.05f), //
            PumpTempBasalType.Percent,
            new DoseSettings(10,  15, 12*60,0f, 250f), PumpCapability.BasalRate_Duration15and30minAllowed, //
            0.02f, 0.1f, null, PumpCapability.InsightCapabilities), //

    // Animas
    AnimasVibe("Animas Vibe", 0.05f, null, // AnimasBolus?
            new DoseSettings(0.05f, 30, 12*60, 0.05f), //
            PumpTempBasalType.Percent, //
            new DoseSettings(10, 30, 24*60, 0f, 200f), PumpCapability.BasalRate_Duration30minAllowed, //
            0.025f, 5f, 0f, null, PumpCapability.VirtualPumpCapabilities), //

    AnimasPing("Animas Ping", AnimasVibe),

    // Dana
    DanaR("DanaR", 0.05f, null, //
            new DoseSettings(0.05f, 30, 8*60, 0.05f), //
            PumpTempBasalType.Percent, //
            new DoseSettings(10f, 60, 24*60, 0f, 200f), PumpCapability.BasalRate_Duration15and30minNotAllowed, //
            0.04f, 0.01f, null, PumpCapability.DanaCapabilities),

    DanaRKorean("DanaR Korean", 0.05f, null, //
            new DoseSettings(0.05f, 30, 8*60, 0.05f), //
            PumpTempBasalType.Percent, //
            new DoseSettings(10f, 60, 24*60, 0f, 200f), PumpCapability.BasalRate_Duration15and30minNotAllowed, //
            0.1f, 0.01f, null, PumpCapability.DanaCapabilities),

    DanaRS("DanaRS", 0.05f, null, //
            new DoseSettings(0.05f, 30, 8*60, 0.05f), //
            PumpTempBasalType.Percent, //
            new DoseSettings(10f, 60, 24*60, 0f, 200f), PumpCapability.BasalRate_Duration15and30minAllowed, //
            0.04f, 0.01f, null, PumpCapability.DanaWithHistoryCapabilities),

    DanaRv2("DanaRv2", DanaRS),


    // Insulet
    Insulet_Omnipod("Insulet Omnipod", 0.05f, null, //
            new DoseSettings(0.05f, 30, 8*60, 0.05f), //
            PumpTempBasalType.Absolute, //
            new DoseSettings(0.05f, 30, 12*60, 0f, 5.0f), PumpCapability.BasalRate_Duration30minAllowed, // cannot exceed max basal rate 30u/hr
            0.05f, 0.05f, null, PumpCapability.VirtualPumpCapabilities),

    // Medtronic
    Medtronic_512_712("Medtronic 512/712", 0.05f, null, //
            new DoseSettings(0.05f, 30, 8*60, 0.05f), //
            PumpTempBasalType.Absolute, //
            new DoseSettings(0.05f, 30, 24*60, 0f, 35f), PumpCapability.BasalRate_Duration30minAllowed, //
            0.05f, 0.05f, null, PumpCapability.VirtualPumpCapabilities), // TODO

    Medtronic_515_715("Medtronic 515/715", Medtronic_512_712),
    Medtronic_522_722("Medtronic 522/722", Medtronic_512_712),

    Medtronic_523_723_Revel("Medtronic 523/723 (Revel)", 0.05f, null, //
            new DoseSettings(0.05f, 30, 8*60, 0.05f), //
            PumpTempBasalType.Absolute, //
            new DoseSettings(0.05f, 30, 24*60, 0f, 35f), PumpCapability.BasalRate_Duration30minAllowed, //
            0.025f, 0.025f, DoseStepSize.MedtronicVeoBasal, PumpCapability.VirtualPumpCapabilities), //

    Medtronic_554_754_Veo("Medtronic 554/754 (Veo)", Medtronic_523_723_Revel), // TODO

    Medtronic_640G("Medtronic 640G", 0.025f, null, //
            new DoseSettings(0.05f, 30, 8*60, 0.05f), //
            PumpTempBasalType.Absolute, //
            new DoseSettings(0.05f, 30, 24*60, 0f, 35f), PumpCapability.BasalRate_Duration30minAllowed, //
            0.025f, 0.025f, DoseStepSize.MedtronicVeoBasal, PumpCapability.VirtualPumpCapabilities), //

    // Tandem
    TandemTSlim("Tandem t:slim", 0.01f, null, //
            new DoseSettings(0.01f,15, 8*60, 0.4f),
            PumpTempBasalType.Percent,
            new DoseSettings(1,15, 8*60, 0f, 250f), PumpCapability.BasalRate_Duration15and30minAllowed, //
            0.1f, 0.001f, null, PumpCapability.VirtualPumpCapabilities),

    TandemTFlex("Tandem t:flex", TandemTSlim), //
    TandemTSlimG4("Tandem t:slim G4", TandemTSlim), //
    TandemTSlimX2("Tandem t:slim X2", TandemTSlim), //
    ;

    private String description;
    private float bolusSize;
    private DoseStepSize specialBolusSize;
    private DoseSettings extendedBolusSettings;
    private PumpTempBasalType pumpTempBasalType;
    private DoseSettings tbrSettings;
    private PumpCapability specialBasalDurations;
    private float baseBasalMinValue; //
    private Float baseBasalMaxValue;
    private float baseBasalStep; //
    private DoseStepSize baseBasalSpecialSteps; //
    private PumpCapability pumpCapability;

    private PumpType parent;
    private static Map<String,PumpType> mapByDescription;

    static
    {
        mapByDescription = new HashMap<>();

        for (PumpType pumpType : values()) {
            mapByDescription.put(pumpType.getDescription(), pumpType);
        }
    }


    PumpType(String description, PumpType parent)
    {
        this.description = description;
        this.parent = parent;
    }

    PumpType(String description, PumpType parent, PumpCapability pumpCapability)
    {
        this.description = description;
        this.parent = parent;
        this.pumpCapability = pumpCapability;
    }

    PumpType(String description, float bolusSize, DoseStepSize specialBolusSize, //
             DoseSettings extendedBolusSettings, //
             PumpTempBasalType pumpTempBasalType, DoseSettings tbrSettings,  PumpCapability specialBasalDurations, //
             float baseBasalMinValue, float baseBasalStep, DoseStepSize baseBasalSpecialSteps, PumpCapability pumpCapability)
    {
        this(description, bolusSize, specialBolusSize, extendedBolusSettings, pumpTempBasalType, tbrSettings, specialBasalDurations, baseBasalMinValue, null, baseBasalStep, baseBasalSpecialSteps, pumpCapability);
    }

    PumpType(String description, float bolusSize, DoseStepSize specialBolusSize, //
             DoseSettings extendedBolusSettings, //
             PumpTempBasalType pumpTempBasalType, DoseSettings tbrSettings, PumpCapability specialBasalDurations, //
             float baseBasalMinValue, Float baseBasalMaxValue, float baseBasalStep, DoseStepSize baseBasalSpecialSteps, PumpCapability pumpCapability)
    {
        this.description = description;
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

    public PumpCapability getPumpCapability() {

        if (isParentSet())
            return this.pumpCapability == null ? parent.pumpCapability : pumpCapability;
        else
            return this.pumpCapability;
    }

    public float getBolusSize() {
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


    public float getBaseBasalMinValue() {
        return isParentSet() ? parent.baseBasalMinValue : baseBasalMinValue;
    }


    public Float getBaseBasalMaxValue() {
        return isParentSet() ? parent.baseBasalMaxValue : baseBasalMaxValue;
    }


    public float getBaseBasalStep() {
        return isParentSet() ? parent.baseBasalStep : baseBasalStep;
    }


    public DoseStepSize getBaseBasalSpecialSteps() {
        return isParentSet() ? parent.baseBasalSpecialSteps : baseBasalSpecialSteps;
    }


    public PumpType getParent() {
        return parent;
    }


    private boolean isParentSet()
    {
        return this.parent!=null;
    }


    public static PumpType getByDescription(String desc)
    {
        if (mapByDescription.containsKey(desc))
        {
            return mapByDescription.get(desc);
        }
        else
        {
            return PumpType.GenericAAPS;
        }
    }


    public String getFullDescription(String i18nTemplate, boolean hasExtendedBasals) {

        String unit = getPumpTempBasalType()==PumpTempBasalType.Percent ? "%" : "";

        DoseSettings eb = getExtendedBolusSettings();
        DoseSettings tbr = getTbrSettings();

        String extendedNote = hasExtendedBasals ? MainApp.gs(R.string.virtualpump_pump_def_extended_note) : "";

        return String.format(i18nTemplate, //
                getStep("" + getBolusSize(), getSpecialBolusSize()), //
                eb.getStep(), eb.getDurationStep(), eb.getMaxDuration()/60, //
                getStep(getBaseBasalRange(), getBaseBasalSpecialSteps()), //
                tbr.getMinDose() + unit + "-" + tbr.getMaxDose() + unit, tbr.getStep() + unit,
                tbr.getDurationStep(), tbr.getMaxDuration()/60, extendedNote);
    }


    private String getBaseBasalRange()
    {
        Float maxValue = getBaseBasalMaxValue();

        return maxValue==null ? "" + getBaseBasalMinValue() : getBaseBasalMinValue() + "-" + maxValue;
    }


    private String getStep(String step, DoseStepSize stepSize)
    {
        if (stepSize!=null)
            return step + " [" + stepSize.getDescription() + "] *";
        else
            return "" + step;
    }


    public boolean hasExtendedBasals() {
        return ((getBaseBasalSpecialSteps() !=null) || (getSpecialBolusSize() != null));
    }


    public PumpCapability getSpecialBasalDurations() {

        if (isParentSet())
        {
            return parent.getSpecialBasalDurations();
        }
        else
        {
            return specialBasalDurations == null ? //
                    PumpCapability.BasalRate_Duration15and30minNotAllowed : specialBasalDurations;
        }
    }
}
