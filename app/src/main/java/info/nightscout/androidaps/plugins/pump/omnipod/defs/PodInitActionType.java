package info.nightscout.androidaps.plugins.pump.omnipod.defs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public enum PodInitActionType {

    PairAndPrimeWizardStep, //
    PairPod(PairAndPrimeWizardStep), //
    PrimePod(PairAndPrimeWizardStep), //

    FillCannulaWizardStep,
    FillCannula(FillCannulaWizardStep),
    SetBasalProfile(FillCannulaWizardStep);


    private PodInitActionType[] parent;

    private static Map<PodInitActionType, List<PodInitActionType>> stepsForWizardStep;


    static {
        // TODO

    }


    PodInitActionType() {

    }


    private PodInitActionType(PodInitActionType... parent) {
        this.parent = parent;
    }


    public static List<PodInitActionType> getAvailableWizardSteps(OmnipodPodType podType) {
        List<PodInitActionType> outList = new ArrayList<>();

        if (podType == OmnipodPodType.Eros) {
            outList.add(PodInitActionType.PairAndPrimeWizardStep);
            outList.add(PodInitActionType.FillCannulaWizardStep);
        } else {
            // TODO we might have different wizard steps, with different handling for Dash
        }

        return outList;
    }


    public static List<PodInitActionType> getAvailableActionsForWizardSteps(PodInitActionType wizardStep) {
        if (stepsForWizardStep.containsKey(wizardStep)) {
            return stepsForWizardStep.get(wizardStep);
        } else {
            return new ArrayList<>();
        }
    }


}
