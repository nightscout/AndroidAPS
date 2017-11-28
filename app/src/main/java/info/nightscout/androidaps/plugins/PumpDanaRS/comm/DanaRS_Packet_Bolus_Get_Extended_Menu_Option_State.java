package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;

import com.cozmo.danar.util.BleCommandUtil;

import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

public class DanaRS_Packet_Bolus_Get_Extended_Menu_Option_State extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Bolus_Get_Extended_Menu_Option_State.class);

    public DanaRS_Packet_Bolus_Get_Extended_Menu_Option_State() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_MENU_OPTION_STATE;
    }

    @Override
    public void handleMessage(byte[] data) {
        DanaRPump pump = DanaRPump.getInstance();

        int dataIndex = DATA_START;
        int dataSize = 1;
        int extendedMenuOption = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.isExtendedInProgress = byteArrayToInt(getBytes(data, dataIndex, dataSize)) == 0x01;

        if (Config.logDanaMessageDetail) {
            log.debug("extendedMenuOption: " + extendedMenuOption);
            log.debug("Is extended bolus running: " + pump.isExtendedInProgress);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__GET_EXTENDED_MENU_OPTION_STATE";
    }
}
