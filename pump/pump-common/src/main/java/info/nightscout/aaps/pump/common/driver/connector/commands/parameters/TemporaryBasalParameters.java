package info.nightscout.aaps.pump.common.driver.connector.commands.parameters;


import info.nightscout.aaps.pump.common.driver.connector.defs.PumpCommandType;


public class TemporaryBasalParameters extends CommandParameters {

    int percent;
    int duration;
    double absoluteAmount;
    boolean cancel;

    public TemporaryBasalParameters(PumpCommandType commandType,
                                    int percent, int duration, double absoluteAmount, boolean cancel) {
        super(true, commandType, null);
        this.percent = percent;
        this.duration = duration;
        this.absoluteAmount = absoluteAmount;
        this.cancel = cancel;
    }


    public static TemporaryBasalParameters.TemporaryBasalParametersBuilder builder() {
        return new TemporaryBasalParameters.TemporaryBasalParametersBuilder();
    }


    public static class TemporaryBasalParametersBuilder {

        PumpCommandType commandType;
        int percent;
        int duration;
        double absoluteAmount;
        boolean cancel;

        TemporaryBasalParametersBuilder() {
        }

        public TemporaryBasalParameters.TemporaryBasalParametersBuilder commandType(PumpCommandType commandType) {
            this.commandType = commandType;
            return this;
        }

        public TemporaryBasalParameters.TemporaryBasalParametersBuilder percent(Integer percent) {
            this.percent = percent;
            return this;
        }

        public TemporaryBasalParameters.TemporaryBasalParametersBuilder duration(Integer duration) {
            this.duration = duration;
            return this;
        }

        public TemporaryBasalParameters.TemporaryBasalParametersBuilder absoluteAmount(Double amount) {
            this.absoluteAmount = amount;
            return this;
        }

        public TemporaryBasalParameters.TemporaryBasalParametersBuilder cancel(boolean isCancel) {
            this.cancel = isCancel;
            return this;
        }

        public TemporaryBasalParameters build() {
            return new TemporaryBasalParameters(commandType,
                    this.percent, this.duration, this.absoluteAmount, this.cancel);
        }

    }


}
