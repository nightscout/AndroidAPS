package info.nightscout.androidaps.events;

/**
 * Created by adrian on 07/02/17.
 */

public class EventBolusRequested {
    private double amount;

    public EventBolusRequested (double amount){
        this.amount = amount;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
