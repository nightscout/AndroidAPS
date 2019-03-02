package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.utils.DateUtil;

public class DanaRS_Packet_General_Get_Shipping_Information extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_General_Get_Shipping_Information() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_SHIPPING_INFORMATION;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] data) {
        if (data.length < 18) {
            failed = true;
            return;
        }
        DanaRPump pump = DanaRPump.getInstance();

        int dataIndex = DATA_START;
        int dataSize = 10;
        pump.serialNumber = stringFromBuff(data, dataIndex, dataSize);

        dataIndex += dataSize;
        dataSize = 3;
        pump.shippingDate = dateFromBuff(data, dataIndex);

        dataIndex += dataSize;
        dataSize = 3;
        pump.shippingCountry = asciiStringFromBuff(data, dataIndex, dataSize);

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Serial number: " + pump.serialNumber);
            log.debug("Shipping date: " + DateUtil.dateAndTimeString(pump.shippingDate));
            log.debug("Shipping country: " + pump.shippingCountry);
        }
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__GET_SHIPPING_INFORMATION";
    }
}
