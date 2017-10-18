package org.monkey.d.ruffy.ruffy.driver.display.menu;

/**
 * Created by fishermen21 on 24.05.17.
 */

public class MenuDate {
    private final int day;
    private final int month;


    public MenuDate(int day, int month) {
        this.day = day;
        this.month = month;
    }

    public MenuDate(String value) {
        String[] p = value.split("\\.");
        day = Integer.parseInt(p[0]);
        month = Integer.parseInt(p[1]);
    }

    @Override
    public String toString() {
        return day+"."+String.format("%02d",month)+".";
    }
}
