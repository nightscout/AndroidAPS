package info.nightscout.androidaps.plugins.pump.omnipod.defs.state;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoFaultEvent;

public abstract class PodState {
    protected final int address;
    protected int packetNumber;
    protected int messageNumber;

    protected PodInfoFaultEvent faultEvent;

    public PodState(int address, int packetNumber, int messageNumber) {
        this.address = address;
        this.packetNumber = packetNumber;
        this.messageNumber = messageNumber;
    }

    public abstract boolean hasNonceState();

    public abstract int getCurrentNonce();

    public abstract void advanceToNextNonce();

    public abstract void resyncNonce(int syncWord, int sentNonce, int sequenceNumber);

    public abstract void updateFromStatusResponse(StatusResponse statusResponse);

    public int getAddress() {
        return address;
    }

    public int getMessageNumber() {
        return messageNumber;
    }

    public void setMessageNumber(int messageNumber) {
        this.messageNumber = messageNumber;
    }

    public int getPacketNumber() {
        return packetNumber;
    }

    public void setPacketNumber(int packetNumber) {
        this.packetNumber = packetNumber;
    }

    public void increaseMessageNumber() {
        setMessageNumber((messageNumber + 1) & 0b1111);
    }

    public void increasePacketNumber() {
        setPacketNumber((packetNumber + 1) & 0b11111);
    }

    public boolean hasFaultEvent() {
        return faultEvent != null;
    }

    public PodInfoFaultEvent getFaultEvent() {
        return faultEvent;
    }

    public void setFaultEvent(PodInfoFaultEvent faultEvent) {
        this.faultEvent = faultEvent;
    }
}
