package app.aaps.pump.omnipod.eros.driver.communication.message;

public abstract class NonceResyncableMessageBlock extends MessageBlock {
    public abstract int getNonce();

    public abstract void setNonce(int nonce);
}
