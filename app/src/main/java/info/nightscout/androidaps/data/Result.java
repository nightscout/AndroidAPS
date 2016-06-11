package info.nightscout.androidaps.data;

public class Result extends Object {
    public boolean success = false;
    public boolean enacted = false;
    public String comment = "";
    public Integer duration = -1;
    public Double absolute = -1d;
    public Integer percent = -1;

    public Double bolusDelivered = 0d;

    public String log() {
        return "Success: " + success + " Enacted: " + enacted + " Comment: " + comment + " Duration: " + duration + " Absolute: " + absolute + " Percent: " + percent;
    }
}
