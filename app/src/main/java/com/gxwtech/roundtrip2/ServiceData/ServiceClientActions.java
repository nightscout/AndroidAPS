package com.gxwtech.roundtrip2.ServiceData;

import android.os.Bundle;

import org.joda.time.LocalDateTime;

import java.util.UUID;

/**
 * Created by geoff on 6/25/16.
 */
public class ServiceClientActions {
    public ServiceClientActions() {}

    /*
     *     Set Temp Basal
     *
     *     inputs:
     *     amountUnitsPerHour - temp basal amount, in Units per hour
     *     durationMinutes - temp basal duration, in minutes
     *
     *     result: standard ok/error result
     */
    
    public static String makeRandomID() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    public static ServiceCommand makeSetTempBasalCommand(double amountUnitsPerHour, int durationMinutes) {
        ServiceCommand command = new ServiceCommand("SetTempBasal",makeRandomID());
        Bundle b = command.getMap();
        b.putDouble("amountUnitsPerHour",amountUnitsPerHour);
        b.putInt("durationMinutes",durationMinutes);
        command.setMap(b);
        return command;
    }

    /*
     *  Read Basal Profile
     *
     *  inputs:
     *  which - "STD", "A", or "B"
     *
     *  result: an ok/error result with a basal profile Bundle.
     *  Get the profile using BasalProfile.initFromServiceResult()
     */

    // 'which' is "STD", "A", or "B"
    public static ServiceCommand makeReadBasalProfileCommand(String which) {
        ServiceCommand command = new ServiceCommand("ReadBasalProfile",makeRandomID());
        Bundle b = command.getMap();
        b.putString("which",which);
        command.setMap(b);
        return command;
    }

    public static ServiceCommand makeReadPumpClockCommand() {
        return new ServiceCommand("ReadPumpClock",makeRandomID());
    }

    public static ServiceCommand makeSendBolusCommand(double amountUnits) {
        ServiceCommand command = new ServiceCommand("SendBolus",makeRandomID());
        Bundle b = command.getMap();
        b.putDouble("amountInUnits",amountUnits);
        command.setMap(b);
        return command;
    }

    public static ServiceCommand makeSetPumpClockCommand(LocalDateTime localDateTime) {
        ServiceCommand command = new ServiceCommand("SetPumpClock",makeRandomID());
        Bundle b = command.getMap();
        b.putString("localDateTime",localDateTime.toString());
        command.setMap(b);
        return command;
    }

    public static ServiceCommand makeReadISFProfileCommand() {
        return new ServiceCommand("ReadISFProfile",makeRandomID());
    }

    public static ServiceCommand makeReadBolusWizardCarbProfileCommand() {
        return new ServiceCommand("ReadBolusWizardCarbProfile",makeRandomID());
    }

    public static ServiceCommand makeReadDIASettingCommand() {
        return new ServiceCommand("ReadDIASetting",makeRandomID());
    }

    public static ServiceCommand makeReadBatteryLevelCommand() {
        return new ServiceCommand("ReadBatteryLevel",makeRandomID());
    }

    public static ServiceCommand makeReadReservoirLevelCommand() {
        return new ServiceCommand("ReadReservoirLevel",makeRandomID());
    }

    public static ServiceCommand makeSetPumpIDCommand(String pumpID) {
        ServiceCommand cmd = new ServiceCommand("SetPumpID",makeRandomID());
        Bundle b = cmd.getMap();
        b.putString("pumpID",pumpID);
        cmd.setMap(b);
        return cmd;
    }

    public static ServiceCommand makeUseThisRileylinkCommand(String rlAddress) {
        ServiceCommand cmd = new ServiceCommand("UseThisRileylink",makeRandomID());
        Bundle b = cmd.getMap();
        b.putString("rlAddress",rlAddress);
        cmd.setMap(b);
        return cmd;
    }

    public static ServiceCommand makeRetrieveHistoryPageCommand(int pageNumber) {
        ServiceCommand cmd = new ServiceCommand("RetrieveHistoryPage",makeRandomID());
        Bundle b = cmd.getMap();
        b.putInt("pageNumber",pageNumber);
        cmd.setMap(b);
        return cmd;
    }

    public static ServiceCommand getPumpModel() {
        return new ServiceCommand("ReadPumpModel",makeRandomID());
    }

    public static ServiceCommand makeUpdateAllStatusCommand() {
        return new ServiceCommand("UpdatePumpStatus",makeRandomID());
    }

    public static ServiceCommand makeTunePumpCommand() {
        return new ServiceCommand("WakeAndTune",makeRandomID());
    }

    public static ServiceCommand makeFetchPumpHistoryCommand() {
        return new ServiceCommand("FetchPumpHistory",makeRandomID());
    }

}
