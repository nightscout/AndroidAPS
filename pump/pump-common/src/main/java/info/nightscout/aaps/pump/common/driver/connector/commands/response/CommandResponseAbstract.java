package info.nightscout.aaps.pump.common.driver.connector.commands.response;

import info.nightscout.aaps.pump.common.driver.connector.defs.PumpCommandType;

public class CommandResponseAbstract<E> implements CommandResponseInterface {

    private PumpCommandType commandType;
    private boolean success;
    private String errorDescription;
    private E value;

    public CommandResponseAbstract() {

    }

    public CommandResponseAbstract(PumpCommandType commandType, boolean success,
                                   String errorDescription, E value) {
        this.commandType = commandType;
        this.success = success;
        this.errorDescription = errorDescription;
        this.value = value;
    }


    public PumpCommandType getCommandType() {
        return commandType;
    }

    public void setCommandType(PumpCommandType commandType) {
        this.commandType = commandType;
    }


    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }

    public E getValue() {
        return value;
    }

    public void setValue(E value) {
        this.value = value;
    }

//    public static Builder<E> builder() {
//
//    }


    public CommandResponseAbstract<E> withCommandType(PumpCommandType commandType) {
        this.setCommandType(commandType);
        return this;
    }


    public CommandResponseAbstract<E> withResult(boolean success) {
        this.setSuccess(success);
        return this;
    }


    public CommandResponseAbstract<E> withErrorDescription(String errorDescription) {
        this.setErrorDescription(errorDescription);
        return this;
    }

    public CommandResponseAbstract<E> withValue(E value) {
        this.setValue(value);
        return this;
    }


    public static class Builder<E> {

        PumpCommandType commandType;
        boolean success;
        String errorDescription;
        E value;

        Builder() {
        }

        public Builder<E> commandType(PumpCommandType commandType) {
            this.commandType = commandType;
            return this;
        }

        public Builder<E> success(boolean success) {
            this.success = success;
            return this;
        }


        public Builder<E> errorDescription(String errorDescription) {
            this.errorDescription = errorDescription;
            return this;
        }

        public CommandResponseAbstract<E> build() {
            return new CommandResponseAbstract<>(this.commandType,
                    this.success, this.errorDescription, this.value);
        }

    }


}
