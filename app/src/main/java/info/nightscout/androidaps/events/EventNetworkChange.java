package info.nightscout.androidaps.events;

public class EventNetworkChange extends Event {

    public boolean mobileConnected = false;
    public boolean wifiConnected = false;

    public String ssid = "";
    public boolean roaming = false;

    public String getSsid() {
        return ssid.replace("SSID: ","").replaceAll("\"","");
    }
}
