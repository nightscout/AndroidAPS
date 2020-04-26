package info.nightscout.androidaps.plugins.pump.insight.descriptors;

public class Alert {

    private int alertId;
    private AlertCategory alertCategory;
    private AlertType alertType;
    private AlertStatus alertStatus;
    private int tbrAmount;
    private int tbrDuration;
    private double programmedBolusAmount;
    private double deliveredBolusAmount;
    private double cartridgeAmount;

    public int getAlertId() {
        return this.alertId;
    }

    public AlertCategory getAlertCategory() {
        return this.alertCategory;
    }

    public AlertType getAlertType() {
        return this.alertType;
    }

    public AlertStatus getAlertStatus() {
        return this.alertStatus;
    }

    public void setAlertId(int alertId) {
        this.alertId = alertId;
    }

    public void setAlertCategory(AlertCategory alertCategory) {
        this.alertCategory = alertCategory;
    }

    public void setAlertType(AlertType alertType) {
        this.alertType = alertType;
    }

    public void setAlertStatus(AlertStatus alertStatus) {
        this.alertStatus = alertStatus;
    }

    public int getTBRAmount() {
        return tbrAmount;
    }

    public void setTBRAmount(int tbrAmount) {
        this.tbrAmount = tbrAmount;
    }

    public int getTBRDuration() {
        return tbrDuration;
    }

    public void setTBRDuration(int tbrDuration) {
        this.tbrDuration = tbrDuration;
    }

    public double getProgrammedBolusAmount() {
        return programmedBolusAmount;
    }

    public void setProgrammedBolusAmount(double programmedBolusAmount) {
        this.programmedBolusAmount = programmedBolusAmount;
    }

    public double getDeliveredBolusAmount() {
        return deliveredBolusAmount;
    }

    public void setDeliveredBolusAmount(double deliveredBolusAmount) {
        this.deliveredBolusAmount = deliveredBolusAmount;
    }

    public void setCartridgeAmount(double cartridgeAmount) {
        this.cartridgeAmount = cartridgeAmount;
    }

    public double getCartridgeAmount() {
        return cartridgeAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Alert alert = (Alert) o;

        if (alertId != alert.alertId) return false;
        if (tbrAmount != alert.tbrAmount) return false;
        if (tbrDuration != alert.tbrDuration) return false;
        if (Double.compare(alert.programmedBolusAmount, programmedBolusAmount) != 0)
            return false;
        if (Double.compare(alert.deliveredBolusAmount, deliveredBolusAmount) != 0)
            return false;
        if (Double.compare(alert.cartridgeAmount, cartridgeAmount) != 0) return false;
        if (alertCategory != alert.alertCategory) return false;
        if (alertType != alert.alertType) return false;
        return alertStatus == alert.alertStatus;
    }
}
