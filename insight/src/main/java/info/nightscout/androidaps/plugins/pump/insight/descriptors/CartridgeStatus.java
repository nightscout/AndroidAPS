package info.nightscout.androidaps.plugins.pump.insight.descriptors;

public class CartridgeStatus {

    private boolean inserted;
    private CartridgeType cartridgeType;
    private SymbolStatus symbolStatus;
    private double remainingAmount;

    public boolean isInserted() {
        return this.inserted;
    }

    public CartridgeType getCartridgeType() {
        return this.cartridgeType;
    }

    public SymbolStatus getSymbolStatus() {
        return this.symbolStatus;
    }

    public double getRemainingAmount() {
        return this.remainingAmount;
    }

    public void setInserted(boolean inserted) {
        this.inserted = inserted;
    }

    public void setCartridgeType(CartridgeType cartridgeType) {
        this.cartridgeType = cartridgeType;
    }

    public void setSymbolStatus(SymbolStatus symbolStatus) {
        this.symbolStatus = symbolStatus;
    }

    public void setRemainingAmount(double remainingAmount) {
        this.remainingAmount = remainingAmount;
    }
}
