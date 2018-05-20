package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpType;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.RileyLinkBLE;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.tasks.ServiceTask;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpStatus;

/**
 * Created by andy on 17/05/2018.
 */

public class RileyLinkUtil {

    private static Context context;
    private static RileyLinkBLE rileyLinkBLE;
    private static RileyLinkServiceData rileyLinkServiceData;
    private static List<RLHistoryItem> historyRileyLink = new ArrayList<>();
    private static PumpType pumpType;
    private static MedtronicPumpStatus medtronicPumpStatus;

    // BAD dependencies in Classes: RileyLinkService

    // Broadcasts: RileyLinkBLE, RileyLinkService,


    public static void setContext(Context contextIn) {
        context = contextIn;
    }


    public static void sendBroadcastMessage(String message) {
        Intent intent = new Intent(message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }


    public static void setRileyLinkBLE(RileyLinkBLE rileyLinkBLEIn) {
        rileyLinkBLE = rileyLinkBLEIn;
    }


    public static RileyLinkBLE getRileyLinkBLE() {
        return rileyLinkBLE;
    }

    public static RileyLinkServiceData getRileyLinkServiceData() {
        return rileyLinkServiceData;
    }

    public static void setRileyLinkServiceData(RileyLinkServiceData rileyLinkServiceData) {
        RileyLinkUtil.rileyLinkServiceData = rileyLinkServiceData;
    }

    public static void setCurrentTask(ServiceTask task) {
        // FIXME
    }

    public static void finishCurrentTask(ServiceTask task) {
        // FIXME
    }

    public static void addHistoryEntry(RLHistoryItem rlHistoryItem) {
        historyRileyLink.add(rlHistoryItem);
    }

    public static void setPumpType(PumpType pumpType) {
        RileyLinkUtil.pumpType = pumpType;
    }

    public static void setPumpStatus(MedtronicPumpStatus medtronicPumpStatus) {

        RileyLinkUtil.medtronicPumpStatus = medtronicPumpStatus;
    }


    public static MedtronicPumpStatus getPumpStatus() {

        return medtronicPumpStatus;
    }
}
