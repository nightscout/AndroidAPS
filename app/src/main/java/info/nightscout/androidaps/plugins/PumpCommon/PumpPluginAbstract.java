package info.nightscout.androidaps.plugins.PumpCommon;

import android.util.Log;

import org.json.JSONObject;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.PumpCommon.data.PumpStatus;
import info.nightscout.androidaps.plugins.PumpCommon.driver.PumpDriverInterface;
import info.nightscout.androidaps.plugins.PumpMedtronic.MedtronicPumpPlugin;

/**
 * Created by andy on 23.04.18.
 */

public abstract class PumpPluginAbstract implements PluginBase, PumpInterface {

    protected boolean fragmentVisible = false;
    protected boolean fragmentEnabled = false;
    protected boolean pumpServiceRunning = false;
    private static final String TAG = "PumpPluginAbstract";
    //protected PumpStatus pumpStatus;



    protected static PumpPluginAbstract plugin = null;
    protected PumpDriverInterface pumpDriver;


    protected PumpPluginAbstract(PumpDriverInterface pumpDriverInterface)
    {
        this.pumpDriver = pumpDriverInterface;
    }


    @Override
    public int getType() {
        return PluginBase.PUMP;
    }


    @Override
    public boolean isVisibleInTabs(int type) {
        return type == PUMP && fragmentVisible;
    }


    @Override
    public boolean canBeHidden(int type) {
        return true;
    }


    @Override
    public boolean hasFragment() {
        return true;
    }


    @Override
    public boolean showInList(int type) {
        return type == PUMP;
    }


    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == PUMP) {
            this.fragmentEnabled = fragmentEnabled;

            if (fragmentEnabled) {
                if (!pumpServiceRunning)
                    startPumpService();
                else
                    Log.d(TAG, "Can't start, Pump service (" + getInternalName() + "is already running.");
            }
            else {
                if (pumpServiceRunning)
                    stopPumpService();
                else
                    Log.d(TAG, "Can't stop, Pump service (" + getInternalName() + "is already stopped.");
            }
        }
    }


    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == PUMP) this.fragmentVisible = fragmentVisible;
    }

    protected abstract String getInternalName();

    protected abstract void startPumpService();

    protected abstract void stopPumpService();


    public PumpStatus getPumpStatusData()
    {
        return pumpDriver.getPumpStatusData();
    }


    public boolean isInitialized()
    {
        return pumpDriver.isInitialized();
    }

    public boolean isSuspended(){
        return pumpDriver.isSuspended();
    }

    public boolean isBusy(){
        return pumpDriver.isBusy();
    }


    public boolean isConnected(){
        return pumpDriver.isConnected();
    }


    public boolean isConnecting(){
        return pumpDriver.isConnecting();
    }


    public void connect(String reason){
        pumpDriver.connect(reason);
    }


    public void disconnect(String reason){
        pumpDriver.disconnect(reason);
    }


    public void stopConnecting(){
        pumpDriver.stopConnecting();
    }


    public void getPumpStatus(){
        pumpDriver.getPumpStatus();
    }


    // Upload to pump new basal profile
    public PumpEnactResult setNewBasalProfile(Profile profile){
        return pumpDriver.setNewBasalProfile(profile);
    }


    public boolean isThisProfileSet(Profile profile){
        return pumpDriver.isThisProfileSet(profile);
    }


    public Date lastDataTime(){
        return pumpDriver.lastDataTime();
    }


    public double getBaseBasalRate(){
        return pumpDriver.getBaseBasalRate();
    } // base basal rate, not temp basal



    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo){
        return pumpDriver.deliverTreatment(detailedBolusInfo);
    }


    public void stopBolusDelivering(){
        pumpDriver.stopBolusDelivering();
    }


    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, boolean enforceNew){
        return pumpDriver.setTempBasalAbsolute(absoluteRate, durationInMinutes, enforceNew);
    }


    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, boolean enforceNew){
        return pumpDriver.setTempBasalPercent(percent, durationInMinutes, enforceNew);
    }


    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes){
        return pumpDriver.setExtendedBolus(insulin, durationInMinutes);
    }
    //some pumps might set a very short temp close to 100% as cancelling a temp can be noisy
    //when the cancel request is requested by the user (forced), the pump should always do a real cancel


    public PumpEnactResult cancelTempBasal(boolean enforceNew){
        return pumpDriver.cancelTempBasal(enforceNew);
    }


    public PumpEnactResult cancelExtendedBolus(){
        return pumpDriver.cancelExtendedBolus();
    }

    // Status to be passed to NS


    public JSONObject getJSONStatus(){
        return pumpDriver.getJSONStatus();
    }


    public String deviceID(){
        return pumpDriver.deviceID();
    }

    // Pump capabilities


    public PumpDescription getPumpDescription(){
        return pumpDriver.getPumpDescription();
    }

    // Short info for SMS, Wear etc


    public String shortStatus(boolean veryShort){
        return pumpDriver.shortStatus(veryShort);
    }



    public boolean isFakingTempsByExtendedBoluses(){
        return pumpDriver.isInitialized();
    }


}
