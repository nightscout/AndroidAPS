package info.nightscout.aaps.pump.common.driver.connector.commands.response;

import info.nightscout.aaps.pump.common.driver.connector.defs.PumpCommandType;

public class ResultCommandResponse extends CommandResponseAbstract<Boolean> {

    public ResultCommandResponse(PumpCommandType commandType, boolean success, String errorDescription) {
        super(commandType, success, errorDescription, success);
    }

    public DataCommandResponse<Boolean> cloneWithNewCommandType(PumpCommandType pumpCommandType) {
        return new DataCommandResponse<Boolean>(pumpCommandType,
                isSuccess(),
                getErrorDescription(),true);
    }


//    public static CommandResponse.CommandResponseBuilder builder() {
//        return new CommandResponse.CommandResponseBuilder();
//    }

//    public static abstract class CommandResponseBuilder<E> {
//
//        PumpCommandType commandType;
//        boolean success;
//        String errorDescription;
//        E value;
//
//        CommandResponseBuilder() {
//        }
//
//        public CommandResponse.CommandResponseBuilder commandType(PumpCommandType commandType) {
//            this.commandType = commandType;
//            return this;
//        }
//
//        public CommandResponse.CommandResponseBuilder success(boolean success) {
//            this.success = success;
//            return this;
//        }
//
//
//        public CommandResponse.CommandResponseBuilder errorDescription(String errorDescription) {
//            this.errorDescription = errorDescription;
//            return this;
//        }
//
//        public CommandResponse build() {
//            return new CommandResponse(this.commandType,
//                    this.success, this.errorDescription, this.value);
//        }
//
//    }


}
