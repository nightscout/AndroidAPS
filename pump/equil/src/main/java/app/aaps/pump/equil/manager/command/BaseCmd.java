package app.aaps.pump.equil.manager.command;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.queue.CustomCommand;
import app.aaps.core.interfaces.sharedPreferences.SP;
import app.aaps.pump.equil.EquilConst;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.database.ResolvedResult;
import app.aaps.pump.equil.manager.Crc;
import app.aaps.pump.equil.manager.EquilCmdModel;
import app.aaps.pump.equil.manager.EquilManager;
import app.aaps.pump.equil.manager.EquilResponse;
import app.aaps.pump.equil.manager.Utils;


public abstract class BaseCmd implements CustomCommand {
    public ResolvedResult resolvedResult = ResolvedResult.NONE;
    public static final String defaultPort = "0F0F";
    public static int reqIndex;
    public static int pumpReqIndex = 10;
    public static int rspIndex = -1;
    AAPSLogger aapsLogger;
    SP sp;
    EquilManager equilManager;
    private int timeOut = 22000;
    private int connectTimeOut = 15000;

    public String port = "0404";
    public boolean config;
    public boolean isEnd;
    boolean cmdStatus;
    private boolean enacted = true;
    public EquilResponse response;
    public String runPwd;
    public String runCode;
    final long createTime;

    public BaseCmd(long createTime) {
        this.createTime = createTime;
    }

    public abstract EquilResponse getEquilResponse();

    public abstract EquilResponse getNextEquilResponse();

    @Nullable public abstract EquilResponse decodeEquilPacket(byte[] data);

    public abstract EquilResponse decode() throws Exception;

    public abstract EquilResponse decodeConfirm() throws Exception;

    public abstract EquilHistoryRecord.EventType getEventType();


    public ResolvedResult getResolvedResult() {
        return resolvedResult;
    }

    public void setResolvedResult(ResolvedResult resolvedResult) {
        this.resolvedResult = resolvedResult;
    }

    public boolean isEnacted() {
        return enacted;
    }

    public void setEnacted(boolean enacted) {
        this.enacted = enacted;
    }

    public int getConnectTimeOut() {
        return connectTimeOut;
    }

    public void setConnectTimeOut(int connectTimeOut) {
        this.connectTimeOut = connectTimeOut;
    }

    public int getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(int timeOut) {
        this.timeOut = timeOut;
    }

    public boolean isCmdStatus() {
        return cmdStatus;
    }

    public void setCmdStatus(boolean cmdStatus) {
        this.cmdStatus = cmdStatus;
    }

    @NonNull @Override public String getStatusDescription() {
        return this.getClass().getSimpleName();
    }

    public void setEquilManager(EquilManager equilManager) {
        this.equilManager = equilManager;
        this.aapsLogger = equilManager.getAapsLogger();
        this.sp = equilManager.getSp();
    }


    public boolean checkData(byte[] data) {
        if (response.getSend().size() > 0) {
            byte[] preData = response.getSend().get(response.getSend().size() - 1).array();
            int index = data[3] & 0xff;
            int preIndex = preData[3] & 0xff;
            if (index == preIndex) {
                aapsLogger.debug(LTag.PUMPCOMM, "checkData error ");
                return false;
            }
        }
        int crc = data[5] & 0xff;
        int crc1 = Crc.CRC8_MAXIM(Arrays.copyOfRange(data, 0, 5));
        if (crc != crc1) {
            aapsLogger.debug(LTag.PUMPCOMM, "checkData crc error");
            return false;
        }
        return true;
    }


    public String getRunCode() {
        return runCode;
    }

    public void setRunCode(String runCode) {
        this.runCode = runCode;
    }

    public String getRunPwd() {
        return runPwd;
    }

    public void setRunPwd(String runPwd) {
        this.runPwd = runPwd;
    }

    public String getEquilDevices() {
        return sp.getString(EquilConst.Prefs.INSTANCE.getEQUIL_DEVICES(), "");
    }

    public String getEquilPassWord() {
        return sp.getString(EquilConst.Prefs.INSTANCE.getEQUIL_PASSWORD(), "");
    }

    public static int up1(double value) {
        BigDecimal bg = new BigDecimal(value);
        return bg.setScale(0, RoundingMode.UP).intValue();
    }

    public boolean isPairStep() {
        return false;
    }

    public EquilResponse responseCmd(EquilCmdModel equilCmdModel, String port) {
        StringBuilder allData = new StringBuilder();
        allData.append(port);
        allData.append(equilCmdModel.getTag());
        allData.append(equilCmdModel.getIv());
        allData.append(equilCmdModel.getCiphertext());
        byte[] allByte = Utils.hexStringToBytes(allData.toString());
        byte[] crc1 = Crc.getCRC(allByte);
        allByte = Utils.hexStringToBytes(allData.toString());
        int byteIndex = 0;
        int lastLen = 0;
        int index;
        if ((allByte.length - 8) % 10 == 0) {
            index = 1;
        } else {
            index = 2;
        }
        EquilResponse equilResponse = new EquilResponse(createTime);
        int maxLen = up1((allByte.length - 8) / 10) + index;
        for (int i = 0; i < maxLen; i++) {
            ByteBuffer buffer = ByteBuffer.allocate(16);
            if (i > 0 && lastLen < 10) {
                buffer = ByteBuffer.allocate(6 + lastLen);

            }
            buffer.put((byte) 0x00);
            buffer.put((byte) 0x00);
            if (i == maxLen - 1) {
                buffer.put((byte) (6 + lastLen));
                buffer.put((byte) ((10 * i)));
                buffer.put((byte) toNewEndConf((byte) reqIndex));
            } else {
                buffer.put((byte) 0x10);
                buffer.put((byte) (10 * i));
                buffer.put((byte) toNewStart((byte) reqIndex));
            }
            byte[] crcArray = new byte[5];
            System.arraycopy(buffer.array(), 0, crcArray, 0, 5);
            buffer.put((byte) Crc.CRC8_MAXIM(crcArray));
            if (i == 0) {
                buffer.put((byte) allByte[byteIndex]);
                byteIndex++;
                buffer.put((byte) allByte[byteIndex]);
                byteIndex++;
                buffer.put((byte) allByte[byteIndex]);
                byteIndex++;
                buffer.put((byte) allByte[byteIndex]);
                byteIndex++;
                buffer.put(crc1[1]);
                buffer.put(crc1[0]);
                buffer.put((byte) allByte[byteIndex]);
                byteIndex++;
                buffer.put((byte) allByte[byteIndex]);
                byteIndex++;
                buffer.put((byte) allByte[byteIndex]);
                byteIndex++;
                buffer.put((byte) allByte[byteIndex]);
                byteIndex++;
            } else {
                if (lastLen < 10) {
                    for (int j = 0; j < lastLen; j++) {
                        buffer.put((byte) allByte[byteIndex]);
                        byteIndex++;
                    }
                } else {
                    for (int j = 0; j < 10; j++) {
                        buffer.put((byte) allByte[byteIndex]);
                        byteIndex++;
                    }
                }
            }
            lastLen = allByte.length - byteIndex;
            equilResponse.add(buffer);
        }
        reqIndex++;
        return equilResponse;
    }


    public EquilCmdModel decodeModel() {
        EquilCmdModel equilCmdModel = new EquilCmdModel();
        List<Byte> list = new ArrayList<>();
        int index = 0;
        for (ByteBuffer b : response.getSend()) {
            if (index == 0) {
                byte[] bs = b.array();
                for (int i = bs.length - 4; i < bs.length; i++) {
                    list.add((Byte) bs[i]);
                }
                byte[] codeByte = new byte[]{bs[10], bs[11]};
                equilCmdModel.setCode(Utils.bytesToHex(codeByte));
            } else {
                byte[] bs = b.array();
                for (int i = 6; i < bs.length; i++) {
                    list.add((Byte) bs[i]);
                }
            }
            index++;
        }
        List<Byte> list1 = list.subList(0, 16);
        List<Byte> list2 = list.subList(16, 12 + 16);
        List<Byte> list3 = list.subList(12 + 16, list.size());
        equilCmdModel.setIv(Utils.bytesToHex(list2).toLowerCase());
        equilCmdModel.setTag(Utils.bytesToHex(list1).toLowerCase());
        equilCmdModel.setCiphertext(Utils.bytesToHex(list3).toLowerCase());
        return equilCmdModel;
    }

    public byte toNewStart(byte number) {
        number &= ~(1 << 7); // 清除指定位（设置为0）
        return number;
    }

    public byte toNewEndConf(byte number) {
//        number &= ~(1 << 6); // 清除指定位（设置为0）
        number |= (1 << 7); // 设置指定位为1
        return number;
    }

    public boolean isEnd(byte b) {
        int bit = getBit(b, 7);
        return bit == 1;
    }

    public int getIndex(byte b) {
        int result = (b & 63); // 提取前6位并右移2位
        return result;
    }

    public int getBit(byte b, int i) {
        int bit = (int) ((b >> i) & 0x1);
        return bit;
    }

    public String convertString(String input) {
        StringBuilder sb = new StringBuilder();
        for (char ch : input.toCharArray()) {
            sb.append("0").append(ch);
        }
        return sb.toString();
    }

}
