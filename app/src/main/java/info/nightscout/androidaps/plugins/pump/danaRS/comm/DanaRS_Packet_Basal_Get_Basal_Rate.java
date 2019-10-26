package info.nightscout.androidaps.plugins.pump.danaRS.comm;


import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

public class DanaRS_Packet_Basal_Get_Basal_Rate extends DanaRS_Packet {
	private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);


    public DanaRS_Packet_Basal_Get_Basal_Rate() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__GET_BASAL_RATE;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Requesting basal rates");
        }
    }

	@Override
	public void handleMessage(byte[] data) {
		DanaRPump pump = DanaRPump.getInstance();

		int dataIndex = DATA_START;
		int dataSize = 2;
		pump.maxBasal = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

		dataIndex += dataSize;
		dataSize = 1;
		pump.basalStep = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;

		if (pump.pumpProfiles == null) pump.pumpProfiles = new double[4][];
		pump.pumpProfiles[pump.activeProfile] = new double[24];
		for (int i = 0, size = 24; i < size; i++) {
			dataIndex += dataSize;
			dataSize = 2;
			pump.pumpProfiles[pump.activeProfile][i] = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100d;
		}
		if (L.isEnabled(L.PUMPCOMM)) {
			log.debug("Max basal: " + pump.maxBasal + " U");
			log.debug("Basal step: " + pump.basalStep + " U");
			for (int index = 0; index < 24; index++)
				log.debug("Basal " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + pump.pumpProfiles[pump.activeProfile][index]);
		}

		if (pump.basalStep != 0.01d) {
		    failed = true;
			Notification notification = new Notification(Notification.WRONGBASALSTEP, MainApp.gs(R.string.danar_setbasalstep001), Notification.URGENT);
			RxBus.INSTANCE.send(new EventNewNotification(notification));
		} else {
			RxBus.INSTANCE.send(new EventDismissNotification(Notification.WRONGBASALSTEP));
		}

	}

	@Override
	public String getFriendlyName() {
		return "BASAL__GET_BASAL_RATE";
	}
}
