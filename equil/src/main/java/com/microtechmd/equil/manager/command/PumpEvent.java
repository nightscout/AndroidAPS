package com.microtechmd.equil.manager.command;

import java.util.Objects;

public class PumpEvent {
    private int port;
    private int type;
    private int level;//0 通知 1.告警 2.报警
    private String conent;

    public PumpEvent(int port, int type, int level, String conent) {
        this.port = port;
        this.type = type;
        this.level = level;
        this.conent = conent;
    }





    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PumpEvent pumpEvent = (PumpEvent) o;
        return port == pumpEvent.port && type == pumpEvent.type && level == pumpEvent.level;
    }

    @Override
    public int hashCode() {
        return Objects.hash(port, type, level);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getConent() {
        return conent;
    }

    public void setConent(String conent) {
        this.conent = conent;
    }


}
