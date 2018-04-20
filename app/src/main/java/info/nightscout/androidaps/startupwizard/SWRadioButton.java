package info.nightscout.androidaps.startupwizard;

import info.nightscout.androidaps.MainApp;

public class SWRadioButton extends SWItem {

    int labelsArray;
    int valuesArray;

    public SWRadioButton() {
        super(Type.RADIOBUTTON);
    }

    public SWRadioButton option(int labels, int values) {
        this.labelsArray = labels;
        this.valuesArray = values;
        return this;
    }

    public String[] labels() {
        return MainApp.sResources.getStringArray(labelsArray);
    }

    public String[] values() {
        return MainApp.sResources.getStringArray(valuesArray);
    }

}
