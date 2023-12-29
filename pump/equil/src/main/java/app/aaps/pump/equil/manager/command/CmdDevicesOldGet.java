package app.aaps.pump.equil.manager.command;


import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import app.aaps.core.interfaces.logging.LTag;
import app.aaps.pump.equil.EquilConst;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.EquilCmdModel;
import app.aaps.pump.equil.manager.EquilResponse;
import app.aaps.pump.equil.manager.Utils;


public class CmdDevicesOldGet extends BaseSetting {
    public String address;
    private float firmwareVersion;

    public CmdDevicesOldGet(String address) {
        super(System.currentTimeMillis());
        port = "0E0E";
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    public EquilResponse getEquilResponse() {
        config = false;
        isEnd = false;
        response = new EquilResponse(createTime);
        EquilResponse temp = new EquilResponse(createTime);
        ByteBuffer buffer = ByteBuffer.allocate(14);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x0E);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x80);
        buffer.put((byte) 0x78);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x01);
        buffer.put((byte) 0x7B);
        buffer.put((byte) 0x02);
        buffer.put((byte) 0x00);
        temp.add(buffer);
        return temp;
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x02, 0x00};
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x00, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
        return data;
    }

    public EquilResponse decodeEquilPacket(byte[] data) {
        if (!checkData(data)) {
            return null;
        }
        byte code = data[4];
        int intValue = getIndex(code);
        if (config) {
            if (rspIndex == intValue) {
                return null;
            }
            boolean flag = isEnd(code);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            response.add(buffer);
            if (!flag) {
                return null;
            }
            try {
                EquilResponse list = decodeConfirm();
                isEnd = true;
                response = new EquilResponse(createTime);
                rspIndex = intValue;
                aapsLogger.debug(LTag.PUMPCOMM, "intValue=====" + intValue + "====" + rspIndex);
                return list;
            } catch (Exception e) {
                response = new EquilResponse(createTime);
                aapsLogger.debug(LTag.PUMPCOMM, "decodeConfirm error =====" + e.getMessage());

            }

            return null;
        }
        boolean flag = isEnd(code);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        response.add(buffer);
        if (!flag) {
            return null;
        }
        try {
            EquilResponse list = decode();
            response = new EquilResponse(createTime);
            config = true;
            rspIndex = intValue;
            aapsLogger.debug(LTag.PUMPCOMM, "intValue=====" + intValue + "====" + rspIndex);
            return list;
        } catch (Exception e) {
            e.printStackTrace();
            response = new EquilResponse(createTime);
            aapsLogger.debug(LTag.PUMPCOMM, "decode error=====" + e.getMessage());
        }
        return null;

    }

    public EquilResponse decode() {
        EquilCmdModel reqModel = decodeModel();
        byte[] data = Utils.hexStringToBytes(reqModel.getCiphertext());
        String fv = data[12] + "." + data[13];
        firmwareVersion = Float.parseFloat(fv);
        aapsLogger.debug(LTag.PUMPCOMM, "CmdDevicesOldGet====" +
                Utils.bytesToHex(data) + "========" + firmwareVersion + "===" + (firmwareVersion < EquilConst.EQUIL_SUPPORT_LEVEL));
        reqModel.setCiphertext(Utils.bytesToHex(getNextData()));
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
        return responseCmd(reqModel, "0000" + reqModel.getCode());
    }

    public EquilCmdModel decodeModel() {
        EquilCmdModel equilCmdModel = new EquilCmdModel();
        List<Byte> list = new ArrayList<>();
        int index = 0;
        for (ByteBuffer b : response.getSend()) {
            if (index == 0) {
                byte[] bs = b.array();
                byte[] codeByte = new byte[]{bs[10], bs[11]};
                list.add((Byte) bs[bs.length - 2]);
                list.add((Byte) bs[bs.length - 1]);
                equilCmdModel.setCode(Utils.bytesToHex(codeByte));
            } else {
                byte[] bs = b.array();
                for (int i = 6; i < bs.length; i++) {
                    list.add((Byte) bs[i]);
                }
            }
            index++;
        }
        List<Byte> list3 = list.subList(0, list.size());
        equilCmdModel.setCiphertext(Utils.bytesToHex(list3).toLowerCase());
        equilCmdModel.setTag("");
        equilCmdModel.setIv("");
        return equilCmdModel;
    }

    @Override
    public void decodeConfirmData(byte[] data) {
        int value = Utils.bytesToInt(data[7], data[6]);
        String fv = data[18] + "." + data[19];
        firmwareVersion = Float.parseFloat(fv);
        aapsLogger.debug(LTag.PUMPCOMM, "CmdDevicesOldGet====" +
                Utils.bytesToHex(data) + "=====" + value + "===" + firmwareVersion + "===="
                + (firmwareVersion < EquilConst.EQUIL_SUPPORT_LEVEL));
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }

    public boolean isSupport() {
        return !(firmwareVersion < EquilConst.EQUIL_SUPPORT_LEVEL);
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return null;
    }
}
