package org.monkey.d.ruffy.ruffy.driver.display.menu;

/**
 * Created by fishermen21 on 22.05.17.
 */

public class MenuTime {

    private final int hour;
    private final int minute;

    public MenuTime(int hour, int minute)
    {
        this.hour = hour;
        this.minute = minute;
    }

    public MenuTime(String value) {
        String[] p = value.split(":");
        hour = Integer.parseInt(p[0]);
        minute = Integer.parseInt(p[1]);
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }

    @Override
    public String toString() {
        return hour+":"+String.format("%02d",minute);
    }
}
