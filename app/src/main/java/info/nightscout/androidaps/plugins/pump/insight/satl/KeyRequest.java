package info.nightscout.androidaps.plugins.pump.insight.satl;

import java.util.Calendar;

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class KeyRequest extends SatlMessage {

    private byte[] randomBytes;
    private byte[] preMasterKey;

    @Override
    protected ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(288);
        byteBuf.putBytes(randomBytes);
        byteBuf.putUInt32LE(translateDate());
        byteBuf.putBytes(preMasterKey);
        return byteBuf;
    }

    private static int translateDate() {
        Calendar calendar = Calendar.getInstance();
        int second = calendar.get(Calendar.SECOND);
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int month = calendar.get(Calendar.MONTH);
        int year = calendar.get(Calendar.YEAR);
        return (year % 100 & 0x3f) << 26 | (month & 0x0f) << 22 | (day & 0x1f) << 17 | (hour & 0x1f) << 12 | (minute & 0x3f) << 6 | (second & 0x3f);
    }

    public void setRandomBytes(byte[] randomBytes) {
        this.randomBytes = randomBytes;
    }

    public void setPreMasterKey(byte[] preMasterKey) {
        this.preMasterKey = preMasterKey;
    }
}
