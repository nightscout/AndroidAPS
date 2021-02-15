package info.nightscout.androidaps.plugins.pump.medtronic.comm.message;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RLMessage;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType;

/**
 * Created by geoff on 5/29/16.
 */
public class PumpMessage implements RLMessage {

    private final AAPSLogger aapsLogger;

    private PacketType packetType = PacketType.Carelink;
    public byte[] address = new byte[]{0, 0, 0};
    public MedtronicCommandType commandType;
    public Byte invalidCommandType;
    public MessageBody messageBody = new MessageBody();
    public String error = null;

    public static final int FRAME_DATA_LENGTH = 64;


    public PumpMessage(AAPSLogger aapsLogger, String error) {
        this.error = error;
        this.aapsLogger = aapsLogger;
    }


    public PumpMessage(AAPSLogger aapsLogger, byte[] rxData) {
        this.aapsLogger = aapsLogger;
        init(rxData);
    }


    public PumpMessage(AAPSLogger aapsLogger) {
        this.aapsLogger = aapsLogger;
    }


    public boolean isErrorResponse() {
        return (this.error != null);
    }


    public void init(PacketType packetType, byte[] address, MedtronicCommandType commandType, MessageBody messageBody) {
        this.packetType = packetType;
        this.address = address;
        this.commandType = commandType;
        this.messageBody = messageBody;
    }


    public void init(byte[] rxData) {
        if (rxData == null) {
            return;
        }
        if (rxData.length > 0) {
            this.packetType = PacketType.getByValue(rxData[0]);
        }
        if (rxData.length > 3) {
            this.address = ByteUtil.substring(rxData, 1, 3);
        }
        if (rxData.length > 4) {
            this.commandType = MedtronicCommandType.getByCode(rxData[4]);
            if (this.commandType == MedtronicCommandType.InvalidCommand) {
                aapsLogger.error(LTag.PUMPCOMM, "PumpMessage - Unknown commandType " + rxData[4]);
            }
        }
        if (rxData.length > 5) {
            this.messageBody = MedtronicCommandType.constructMessageBody(commandType,
                    ByteUtil.substring(rxData, 5, rxData.length - 5));
        }
    }


    @Override
    public byte[] getTxData() {
        byte[] rval = ByteUtil.concat(new byte[]{packetType.getValue()}, address);
        rval = ByteUtil.concat(rval, commandType.getCommandCode());
        rval = ByteUtil.concat(rval, messageBody.getTxData());
        return rval;
    }


    public byte[] getContents() {
        return ByteUtil.concat(new byte[]{commandType.getCommandCode()}, messageBody.getTxData());
    }


    // rawContent = just response without code (contents-2, messageBody.txData-1);
    public byte[] getRawContent() {

        if ((messageBody == null) || (messageBody.getTxData() == null) || (messageBody.getTxData().length == 0))
            return null;

        byte[] data = messageBody.getTxData();

        int length = ByteUtil.asUINT8(data[0]); // length is not always correct so, we check whole array if we have
        // data, after length
        int originalLength = length;

        // check if displayed length is invalid
        if (length > data.length - 1) {
            return data;
        }

        // check Old Way
        boolean oldWay = false;
        for (int i = (length + 1); i < data.length; i++) {
            if (data[i] != 0x00) {
                oldWay = true;
            }
        }

        if (oldWay) {
            length = data.length - 1;
        }

        byte[] arrayOut = new byte[length];

        System.arraycopy(messageBody.getTxData(), 1, arrayOut, 0, length);

//        if (isLogEnabled())
//            LOG.debug("PumpMessage - Length: " + length + ", Original Length: " + originalLength + ", CommandType: "
//            + commandType);

        return arrayOut;
    }


    public byte[] getRawContentOfFrame() {
        byte[] raw = messageBody.getTxData();
        if (raw==null || raw.length==0) {
            return new byte[0];
        } else {
            return ByteUtil.substring(raw, 1, Math.min(FRAME_DATA_LENGTH, raw.length - 1));
        }
    }


    public boolean isValid() {
        if (packetType == null)
            return false;
        if (address == null)
            return false;
        if (commandType == null)
            return false;
        return messageBody != null;
    }


    public MessageBody getMessageBody() {
        return messageBody;
    }


    public String getResponseContent() {
        StringBuilder sb = new StringBuilder("PumpMessage [response=");
        boolean showData = true;

        if (commandType != null) {
            if (commandType == MedtronicCommandType.CommandACK) {
                sb.append("Acknowledged");
                showData = false;
            } else if (commandType == MedtronicCommandType.CommandNAK) {
                sb.append("NOT Acknowledged");
                showData = false;
            } else {
                sb.append(commandType.name());
            }
        } else {
            sb.append("Unknown_Type");
            sb.append(" (" + invalidCommandType + ")");
        }

        if (showData) {
            sb.append(", rawResponse=");
            sb.append(ByteUtil.shortHexString(getRawContent()));
        }

        sb.append("]");

        return sb.toString();
    }


    public String toString() {
        StringBuilder sb = new StringBuilder("PumpMessage [");

        sb.append("packetType=");
        sb.append(packetType == null ? "null" : packetType.name());

        sb.append(", address=(");
        sb.append(ByteUtil.shortHexString(this.address));

        sb.append("), commandType=");
        sb.append(commandType == null ? "null" : commandType.name());

        if (invalidCommandType != null) {
            sb.append(", invalidCommandType=");
            sb.append(invalidCommandType);
        }

        sb.append(", messageBody=(");
        sb.append(this.messageBody == null ? "null" : this.messageBody);

        sb.append(")]");

        return sb.toString();
    }
}
