package info.nightscout.androidaps.plugins.pump.common.defs;

/**
 * Created by andy on 02/05/2018.
 */

public enum DoseStepSize {

    ComboBasal( //
            new DoseStepSizeEntry(0f, 1f, 0.01f), //
            new DoseStepSizeEntry(1f, 10f, 0.05f), //
            new DoseStepSizeEntry(10f, Double.MAX_VALUE, 0.1f)), //

    InsightBolus(
            new DoseStepSizeEntry(0f, 2f, 0.05f), //
            new DoseStepSizeEntry(2f, 5f, 0.1f), //
            new DoseStepSizeEntry(5f, 10f, 0.2f), //
            new DoseStepSizeEntry(10f, Double.MAX_VALUE, 0.5f)),

    InsightBasal(
            new DoseStepSizeEntry(0f, 5f, 0.01f),
            new DoseStepSizeEntry(5f, Double.MAX_VALUE, 0.1f)),

    MedtronicVeoBasal( //
            new DoseStepSizeEntry(0f, 1f, 0.025f), //
            new DoseStepSizeEntry(1f, 10f, 0.05f), //
            new DoseStepSizeEntry(10f, Double.MAX_VALUE, 0.1f)), //
    ;


    DoseStepSizeEntry[] entries;


    DoseStepSize(DoseStepSizeEntry... entries) {
        this.entries = entries;
    }


    public double getStepSizeForAmount(double amount) {
        for (DoseStepSizeEntry entry : entries) {
            if (entry.from <= amount && entry.to > amount)
                return entry.value;
        }

        // should never come to this
        return entries[entries.length - 1].value;
    }


    public String getDescription() {
        StringBuilder sb = new StringBuilder();

        for (DoseStepSizeEntry entry : entries) {

            sb.append(entry.value);
            sb.append(" {");
            sb.append(entry.from);
            sb.append("-");

            if (entry.to == Double.MAX_VALUE) {
                sb.append("~}");
            } else {
                sb.append(entry.to);
                sb.append("}, ");
            }
        }

        return sb.toString();
    }


    static class DoseStepSizeEntry {
        double from;
        double to;
        double value;

        // to = this value is not included, but would actually mean <, so for rates between 0.025-0.975 u/h, we would have [from=0, to=10]
        DoseStepSizeEntry(double from, double to, double value) {
            this.from = from;
            this.to = to;
            this.value = value;
        }
    }

}
