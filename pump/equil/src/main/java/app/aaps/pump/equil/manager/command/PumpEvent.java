package app.aaps.pump.equil.manager.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import app.aaps.core.interfaces.resources.ResourceHelper;
import app.aaps.pump.equil.R;


public class PumpEvent {
    private int port;
    private int type;
    private int level;//
    private String conent;
    static List<PumpEvent> lists = new ArrayList<>();

    public static void init(ResourceHelper rh) {
        lists = new ArrayList<>();
        lists.add(new PumpEvent(4, 0, 0, "--"));
        lists.add(new PumpEvent(4, 1, 1, rh.gs(R.string.equil_history_item1)));
        lists.add(new PumpEvent(4, 1, 2, rh.gs(R.string.equil_history_item2)));
        lists.add(new PumpEvent(4, 2, 2, rh.gs(R.string.equil_history_item3)));
        lists.add(new PumpEvent(4, 3, 0, rh.gs(R.string.equil_history_item4)));
        lists.add(new PumpEvent(4, 3, 2, rh.gs(R.string.equil_history_item5)));
        lists.add(new PumpEvent(4, 5, 0, rh.gs(R.string.equil_history_item6)));
        lists.add(new PumpEvent(4, 5, 1, rh.gs(R.string.equil_history_item7)));
        lists.add(new PumpEvent(4, 6, 1, rh.gs(R.string.equil_history_item8)));
        lists.add(new PumpEvent(4, 6, 2, rh.gs(R.string.equil_history_item9)));
        lists.add(new PumpEvent(4, 7, 0, rh.gs(R.string.equil_history_item10)));
        lists.add(new PumpEvent(4, 8, 0, rh.gs(R.string.equil_history_item11)));
        lists.add(new PumpEvent(4, 9, 0, rh.gs(R.string.equil_history_item12)));
        lists.add(new PumpEvent(4, 10, 0, rh.gs(R.string.equil_history_item13)));
        lists.add(new PumpEvent(4, 11, 0, rh.gs(R.string.equil_history_item14)));
        lists.add(new PumpEvent(5, 0, 1, rh.gs(R.string.equil_history_item15)));
        lists.add(new PumpEvent(5, 0, 2, rh.gs(R.string.equil_history_item16)));
        lists.add(new PumpEvent(5, 1, 0, rh.gs(R.string.equil_history_item17)));
        lists.add(new PumpEvent(5, 1, 2, rh.gs(R.string.equil_history_item18)));
    }

    public PumpEvent(int port, int type, int level, String conent) {
        this.port = port;
        this.type = type;
        this.level = level;
        this.conent = conent;
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
