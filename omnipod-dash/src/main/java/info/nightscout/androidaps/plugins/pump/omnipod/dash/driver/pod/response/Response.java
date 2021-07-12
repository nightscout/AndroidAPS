package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

public interface Response {
    ResponseType getResponseType();

    byte[] getEncoded();
}
