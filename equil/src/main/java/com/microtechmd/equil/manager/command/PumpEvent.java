package com.microtechmd.equil.manager.command;

import java.util.ArrayList;
import java.util.List;
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


    static List<PumpEvent> lists = new ArrayList<>();

    static {
//        lists.add(new PumpEvent(4, 0, 0, "输注状态更新"));
        lists.add(new PumpEvent(4, 0, 0, "--"));
        lists.add(new PumpEvent(4, 1, 1, "储药器剩余药量低"));
        lists.add(new PumpEvent(4, 1, 2, "储药器药液已用完"));
        lists.add(new PumpEvent(4, 2, 2, "检测到输注堵塞"));
        lists.add(new PumpEvent(4, 3, 0, "检测到电机反转"));
        lists.add(new PumpEvent(4, 3, 2, "检测到电机故障"));
        lists.add(new PumpEvent(4, 5, 0, "输注暂停开始"));
        lists.add(new PumpEvent(4, 5, 1, "输注已经暂停"));
        lists.add(new PumpEvent(4, 6, 1, "即将自动关机"));
        lists.add(new PumpEvent(4, 6, 2, "已经自动关机"));
        lists.add(new PumpEvent(4, 7, 0, "推杆定位开始"));
        lists.add(new PumpEvent(4, 8, 0, "推杆回退开始"));
        lists.add(new PumpEvent(4, 9, 0, "快速大剂量开始"));
        lists.add(new PumpEvent(4, 10, 0, "临时基础率开始"));
        lists.add(new PumpEvent(4, 11, 0, "临时基础率结束"));
        lists.add(new PumpEvent(5, 0, 1, "电池电量低"));
        lists.add(new PumpEvent(5, 0, 2, "电池已耗尽"));
        lists.add(new PumpEvent(5, 1, 0, "上电复位"));
        lists.add(new PumpEvent(5, 1, 2, "非正常输注停止"));
    }

    public static String getTips(int port, int type, int level) {
        PumpEvent pumpEvent = new PumpEvent(port, type, level, "");
        int index = lists.indexOf(pumpEvent);
        if (index == -1) {
            return "";
        }
        return lists.get(index).getConent();
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
