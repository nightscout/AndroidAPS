package de.jotomo.ruffyscripter.commands;

public class PumpAlert {
    public final int code;
    public final String msg;

    public PumpAlert(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public String toString() {
        return "PumpAlert{" +
                "code=" + code +
                ", msg='" + msg + '\'' +
                '}';
    }
}
