package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData;

/**
 * Created by geoff on 5/7/15.
 *
 * This class is intended to be very basic, and only hold the simple data.
 * That way it will be portable, reusable.
 *
 * For instance, for the android UI, there is a parcelable version of this class
 * (PumpSettingsParcel) but I don't want the pump implementation to be dependent
 * on Android.Parcelable.
 *
 * For marshalling the data into other forms, I'm keeping the original
 * byte array (mRawData) around.  So if we have to save/reload this data object
 * we can just write the raw data array, and reload from it.  We already have to
 * parse our values from it, so just keep it handy.  Makes the Parcelable
 * implementation very simple.
 *
 */
@Deprecated
public class PumpSettings {
    private static final String TAG = "PumpSettings";

    // these values help with the parcel extension
    protected static final int MINIMUM_DATA_LENGTH = 20; // bytes
    protected static final int MAXIMUM_DATA_LENGTH = 64; // bytes
    protected byte[] mRawData;

    public byte mAutoOffDuration_hours = 0;
    public byte mAlarmMode = 0;
    public byte mAlarmVolume = 0;
    public boolean mAudioBolusEnable = false;
    public double mAudioBolusSize = 0.0;
    public boolean mVariableBolusEnable = false;
    public double mMaxBolus = 0.0; // MM23 is different (?)
    // MM512 and up (?)
    public double mMaxBasal = 0.0;
    public byte mTimeFormat = 0;
    public int mInsulinConcentration = 100; // 100 or 50
    public boolean mPatternsEnabled = false;
    public BasalProfileTypeEnum mSelectedPattern = BasalProfileTypeEnum.STD;
    public boolean mRFEnable = false;
    public boolean mBlockEnable = false;
    public byte mTempBasalType = 0; // 1 means Percent, 0 means UnitsPerHour
    public byte mTempBasalRate = 0; // TODO: scaling?
    public byte mParadigmEnable = 0;
    // MM12
    // boolean mInsulinActionType (0='Fast' or 1='Regular')
    // MM15
    public byte mInsulinActionType = 0;
    public byte mLowReservoirWarnType = 0;
    public byte mLowReservoirWarnPoint = 0;
    public byte mKeypadLockStatus = 0;

    public PumpSettings() {
        init();
    }

    public void init() {
        // this data buffer has to be sized to maximum even when empty
        // as we may be reading from a parcel.  See PumpSettingsParcel.
        mRawData = new byte[MAXIMUM_DATA_LENGTH];
        /* fill in default values */
    }

    public byte[] getRawData() { return mRawData; }

    public boolean parseFrom(byte[] data) {
        if (!setRawData(data)) { return false; }
        return parseFromRaw();
    }

    // copy as much as we can from a byte array, but don't parse it.
    public boolean setRawData(byte[] data) {
        if (data == null) {
            return false;
        }
        int len = Math.min(MAXIMUM_DATA_LENGTH,data.length);
        System.arraycopy(data,0,mRawData,0,len);
        return true;
    }

    public boolean parseFromRaw() {
        mAutoOffDuration_hours = mRawData[0];
        mAlarmVolume = mRawData[1];
        mAlarmMode = 2;
        if (mRawData[1] == 4) {
            mAlarmVolume = -1;
            mAlarmMode = 1;
        }
        mAudioBolusEnable = (mRawData[2] == 1);
        mAudioBolusSize = 0;
        if (mAudioBolusEnable) {
            mAudioBolusSize = mRawData[3] / 10.0;
        }
        mVariableBolusEnable = (mRawData[4] == 1);
        // MM23 is different
        int maxBolusByte = mRawData[5];
        if (maxBolusByte < 0) { maxBolusByte += 256; }
        mMaxBolus = (maxBolusByte) / 10.0;
        // MM512 and up
        // TODO: check mMaxBasal calculation
        // did I get the bytes in the right order?
        int maxBasalHighByte = mRawData[6];
        if (maxBasalHighByte < 0) {
            maxBasalHighByte += 256;
        }
        int maxBasalLowByte = mRawData[7];
        if (maxBasalLowByte < 0) {
            maxBasalLowByte += 256;
        }
        mMaxBasal = ((maxBasalHighByte * 256) + maxBasalLowByte)/40.0;
        mTimeFormat = mRawData[8];
        // mInsulinConcentration: 0 is 100%, 1 is 50%
        mInsulinConcentration = 100;
        if (mRawData[9] == 1) {
            mInsulinConcentration = 50;
        }
        mPatternsEnabled = (mRawData[10] == 1);
        mSelectedPattern = BasalProfileTypeEnum.STD;
        if (mRawData[11] == 0x01) {
            mSelectedPattern = BasalProfileTypeEnum.A;
        } else if (mRawData[11] == 0x02) {
            mSelectedPattern = BasalProfileTypeEnum.B;
        }
        mRFEnable = (mRawData[12] == 1);
        mBlockEnable = (mRawData[13] == 1);
        mTempBasalType = mRawData[14]; // todo: put into proper class of its own
        mTempBasalRate = mRawData[15];

        mParadigmEnable = mRawData[16];
        mInsulinActionType = mRawData[17];
        mLowReservoirWarnType = mRawData[18];
        mLowReservoirWarnPoint = mRawData[19];
        mKeypadLockStatus = mRawData[20];
        return true;
    }
    public String explain() {
        String rval = "";
        // write out a string describing the current contents
        rval += String.format("Auto-Off Duration (hours): %d\n",mAutoOffDuration_hours);
        rval += String.format("Alarm (Volume: %d, Mode: %d)\n",mAlarmVolume,mAlarmMode);
        rval += String.format("Audio BolusNormalPumpEvent (enabled=%s, size=%g)\n",mAudioBolusEnable,mAudioBolusSize);
        rval += String.format("Variable BolusNormalPumpEvent Enable: %s\n", mVariableBolusEnable);
        rval += String.format("Max BolusNormalPumpEvent: %g\n",mMaxBolus);
        rval += String.format("Max Basal: %g\n",mMaxBasal);
        rval += String.format("Time Format 0x%02X\n", mTimeFormat);
        rval += String.format("Insulin Concentration %d%%\n",mInsulinConcentration);
        rval += String.format("Patterns Enabled: %s\n",mPatternsEnabled);
        rval += String.format("Selected Pattern: %d\n",mSelectedPattern);
        rval += String.format("RF Enabled: %s\n",mRFEnable);
        rval += String.format("Block Enabled: %s\n",mBlockEnable);
        rval += String.format("Temp Basal Type: %s\n",(mTempBasalType == 1)?"Percent":"Units/Hr");
        if (mTempBasalType == 1) {
            rval += String.format("Temp Basal Rate: %d%%\n",mTempBasalRate);
        } else {
            rval += String.format("Temp Basal Rate: %d U/h\n",mTempBasalRate);
        }
        rval += String.format("Paradigm Enabled: %s\n", mParadigmEnable);
        rval += String.format("Insulin Action Type: 0x%02X\n",mInsulinActionType);
        rval += String.format("Low Reservoir Warn Type: 0x%02X\n",mLowReservoirWarnType);
        rval += String.format("Low Reservoir Warn Point: 0x%02X\n",mLowReservoirWarnPoint);
        rval += String.format("Keypad Lock Status: 0x%02X\n",mKeypadLockStatus);
        return rval;
    }


}
