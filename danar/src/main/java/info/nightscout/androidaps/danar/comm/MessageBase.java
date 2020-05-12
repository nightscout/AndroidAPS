package info.nightscout.androidaps.danar.comm;

import android.annotation.TargetApi;
import android.os.Build;

import org.joda.time.DateTime;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.dana.DanaPump;
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.danaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.danar.DanaRPlugin;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.ConfigBuilderInterface;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage;
import info.nightscout.androidaps.utils.CRC;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

/*
 *  00  01   02  03   04   05  06
 *
 *  7E  7E  len  F1  CMD  SUB data CRC CRC 2E  2E
 */

public class MessageBase {
    @Inject public AAPSLogger aapsLogger;
    @Inject public DateUtil dateUtil;
    @Inject public DanaPump danaPump;
    @Inject public DanaRPlugin danaRPlugin;
    @Inject public DanaRKoreanPlugin danaRKoreanPlugin;
    @Inject public DanaRv2Plugin danaRv2Plugin;
    @Inject public RxBusWrapper rxBus;
    @Inject public ResourceHelper resourceHelper;
    @Inject public ActivePluginProvider activePlugin;
    @Inject public ConfigBuilderInterface configBuilder;
    @Inject public CommandQueueProvider commandQueue;
    @Inject public DetailedBolusInfoStorage detailedBolusInfoStorage;
    @Inject public ConstraintChecker constraintChecker;
    @Inject public NSUpload nsUpload;
    @Inject public DatabaseHelperInterface databaseHelper;
    HasAndroidInjector injector;

    public byte[] buffer = new byte[512];
    private int position = 6;

    public boolean received = false;
    public boolean failed = false;

    public MessageBase(HasAndroidInjector injector) {
        injector.androidInjector().inject(this);
        this. injector = injector;
    }

    public void SetCommand(int cmd) {
        this.buffer[4] = (byte) (cmd >> 8 & 0xFF);
        this.buffer[5] = (byte) (cmd & 0xFF);
    }

    public void AddParamByte(byte data) {
        this.buffer[this.position++] = data;
    }

    public void AddParamInt(int data) {
        this.buffer[this.position++] = (byte) (data >> 8 & 0xFF);
        this.buffer[this.position++] = (byte) (data & 0xFF);
    }

    public void AddParamDate(GregorianCalendar date) {
        AddParamByte((byte) (date.get(Calendar.YEAR) - 1900 - 100));
        AddParamByte((byte) (date.get(Calendar.MONTH) + 1));
        AddParamByte((byte) (date.get(Calendar.DAY_OF_MONTH)));
        AddParamByte((byte) (date.get(Calendar.HOUR_OF_DAY)));
        AddParamByte((byte) (date.get(Calendar.MINUTE)));
    }

    public void AddParamDateTime(GregorianCalendar date) {
        AddParamByte((byte) (date.get(Calendar.YEAR) - 1900 - 100));
        AddParamByte((byte) (date.get(Calendar.MONTH) + 1));
        AddParamByte((byte) (date.get(Calendar.DAY_OF_MONTH)));
        AddParamByte((byte) (date.get(Calendar.HOUR_OF_DAY)));
        AddParamByte((byte) (date.get(Calendar.MINUTE)));
        AddParamByte((byte) (date.get(Calendar.SECOND)));
    }

    public void AddParamDateTimeReversed(long timestamp) {
        GregorianCalendar date = new GregorianCalendar();
        date.setTimeInMillis(timestamp);
        AddParamByte((byte) (date.get(Calendar.SECOND)));
        AddParamByte((byte) (date.get(Calendar.MINUTE)));
        AddParamByte((byte) (date.get(Calendar.HOUR_OF_DAY)));
        AddParamByte((byte) (date.get(Calendar.DAY_OF_MONTH)));
        AddParamByte((byte) (date.get(Calendar.MONTH) + 1));
        AddParamByte((byte) (date.get(Calendar.YEAR) - 1900 - 100));
    }

    public byte[] getRawMessageBytes() {
        this.buffer[0] = (byte) 0x7E;
        this.buffer[1] = (byte) 0x7E;

        int length = this.position - 3;

        this.buffer[2] = (byte) length;
        this.buffer[3] = (byte) 0xF1;

        this.AddParamInt(CRC.getCrc16(this.buffer, 3, length));

        this.buffer[length + 5] = (byte) 0x2E;
        this.buffer[length + 6] = (byte) 0x2E;

        return Arrays.copyOf(buffer, length + 7);
    }

    public String getMessageName() {
        return MessageOriginalNames.INSTANCE.getName(getCommand());
    }

    public void handleMessage(byte[] bytes) {
        if (bytes.length > 6) {
            int command = (bytes[5] & 0xFF) | ((bytes[4] << 8) & 0xFF00);
            aapsLogger.debug(LTag.PUMPCOMM, "UNPROCESSED MSG: " + getMessageName() + " Command: " + String.format("%04X", command) + " Data: " + toHexString(bytes));
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "MISFORMATTED MSG: " + toHexString(bytes));
        }
    }

    public void handleMessageNotReceived() {
    }

    public int getCommand() {
        int command = byteFromRawBuff(buffer, 5) | (byteFromRawBuff(buffer, 4) << 8);
        return command;
    }

    public int byteFromRawBuff(byte[] buff, int offset) {
        return buff[offset] & 0xFF;
    }

    public int intFromBuff(byte[] buff, int offset, int length) {
        offset += 6;
        switch (length) {
            case 1:
                return byteFromRawBuff(buff, offset);
            case 2:
                return (byteFromRawBuff(buff, offset) << 8) + byteFromRawBuff(buff, offset + 1);
            case 3:
                return (byteFromRawBuff(buff, offset + 2) << 16) + (byteFromRawBuff(buff, offset + 1) << 8) + byteFromRawBuff(buff, offset);
            case 4:
                return (byteFromRawBuff(buff, offset + 3) << 24) + (byteFromRawBuff(buff, offset + 2) << 16) + (byteFromRawBuff(buff, offset + 1) << 8) + byteFromRawBuff(buff, offset);
        }
        return 0;
    }

    public long dateTimeFromBuff(byte[] buff, int offset) {
        return
                new DateTime(
                        2000 + intFromBuff(buff, offset, 1),
                        intFromBuff(buff, offset + 1, 1),
                        intFromBuff(buff, offset + 2, 1),
                        intFromBuff(buff, offset + 3, 1),
                        intFromBuff(buff, offset + 4, 1),
                        0
                ).getMillis();
    }

    public synchronized long dateTimeSecFromBuff(byte[] buff, int offset) {
        return
                new DateTime(
                        2000 + intFromBuff(buff, offset, 1),
                        intFromBuff(buff, offset + 1, 1),
                        intFromBuff(buff, offset + 2, 1),
                        intFromBuff(buff, offset + 3, 1),
                        intFromBuff(buff, offset + 4, 1),
                        intFromBuff(buff, offset + 5, 1)
                ).getMillis();
    }

    public long dateFromBuff(byte[] buff, int offset) {
        return
                new DateTime(
                        2000 + intFromBuff(buff, offset, 1),
                        intFromBuff(buff, offset + 1, 1),
                        intFromBuff(buff, offset + 2, 1),
                        0,
                        0
                ).getMillis();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String stringFromBuff(byte[] buff, int offset, int length) {
        byte[] strbuff = new byte[length];
        System.arraycopy(buff, offset + 6, strbuff, 0, length);
        return new String(strbuff, StandardCharsets.UTF_8);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String asciiStringFromBuff(byte[] buff, int offset, int length) {
        byte[] strbuff = new byte[length];
        System.arraycopy(buff, offset + 6, strbuff, 0, length);
        for (int pos = 0; pos < length; pos++)
            strbuff[pos] += 65; // "A"
        return new String(strbuff, StandardCharsets.UTF_8);
    }

    public static String toHexString(byte[] buff) {
        StringBuilder sb = new StringBuilder();

        int count = 0;
        for (byte element : buff) {
            sb.append(String.format("%02x ", element));
            if (++count % 4 == 0) sb.append(" ");
        }

        return sb.toString();
    }

    public boolean isReceived() {
        return received;
    }
}
