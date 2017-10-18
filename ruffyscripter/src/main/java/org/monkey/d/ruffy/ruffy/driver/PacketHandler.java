package org.monkey.d.ruffy.ruffy.driver;

/**
 * Created by fishermen21 on 19.05.17.
 */

public interface PacketHandler {
    void sendImidiateAcknowledge(byte sequenceNUmber);

    void log(String s);

    void handleResponse(Packet.Response response, boolean reliableFlagged, byte[] payload);

    void handleErrorResponse(byte errorCode, String errDecoded, boolean reliableFlagged, byte[] payload);

    Object getToDeviceKey();
}
