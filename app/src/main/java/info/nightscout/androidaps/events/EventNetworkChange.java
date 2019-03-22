package info.nightscout.androidaps.events;


import info.nightscout.androidaps.utils.StringUtils;

public class EventNetworkChange extends Event {

    public boolean mobileConnected = false;
    public boolean wifiConnected = false;

    public String ssid = "";
    public boolean roaming = false;

    public String getSsid() {
        return StringUtils.removeSurroundingQuotes(ssid);
    }
}
