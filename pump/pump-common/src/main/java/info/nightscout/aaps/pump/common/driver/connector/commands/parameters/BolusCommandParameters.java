package info.nightscout.aaps.pump.common.driver.connector.commands.parameters;


import info.nightscout.aaps.pump.common.driver.connector.defs.PumpCommandType;

public class BolusCommandParameters extends CommandParameters {

    double bolus;
    double carbs;
    int duration;
    boolean cancel;

    public BolusCommandParameters(PumpCommandType commandType,
                                  double bolus, double carbs, int duration, boolean cancel) {
        super(true, commandType, null);
        this.bolus = bolus;
        this.carbs = carbs;
        this.duration = duration;
        this.cancel = cancel;
    }


    public static BolusCommandParameters.Builder builder() {
        return new BolusCommandParameters.Builder();
    }


    public static class Builder {

        PumpCommandType commandType;
        double bolus;
        double carbs;
        int duration;
        boolean cancel;

        Builder() {
        }

        public BolusCommandParameters.Builder commandType(PumpCommandType commandType) {
            this.commandType = commandType;
            return this;
        }

        public BolusCommandParameters.Builder bolus(double bolus) {
            this.bolus = bolus;
            return this;
        }

        public BolusCommandParameters.Builder carbs(double carbs) {
            this.carbs = carbs;
            return this;
        }

        public BolusCommandParameters.Builder duration(Integer duration) {
            this.duration = duration;
            return this;
        }

        public BolusCommandParameters.Builder cancel(boolean isCancel) {
            this.cancel = isCancel;
            return this;
        }

        public BolusCommandParameters build() {
            return new BolusCommandParameters(commandType,
                    this.bolus, this.carbs, this.duration, this.cancel);
        }

    }


}
