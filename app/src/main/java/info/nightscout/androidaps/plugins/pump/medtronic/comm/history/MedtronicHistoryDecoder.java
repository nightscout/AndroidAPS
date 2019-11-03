package info.nightscout.androidaps.plugins.pump.medtronic.comm.history;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;


/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 * Author: Andy {andy.rozman@gmail.com}
 */

public abstract class MedtronicHistoryDecoder<T extends MedtronicHistoryEntry> implements MedtronicHistoryDecoderInterface<T> {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);

    protected ByteUtil bitUtils;

    // STATISTICS (remove at later time or not)
    protected boolean statisticsEnabled = true;
    protected Map<Integer, Integer> unknownOpCodes;
    protected Map<RecordDecodeStatus, Map<String, String>> mapStatistics;
    protected MedtronicDeviceType deviceType;


    public MedtronicHistoryDecoder() {
    }


    // public abstract <E extends MedtronicHistoryEntry> Class<E> getHistoryEntryClass();

    // public abstract RecordDecodeStatus decodeRecord(T record);

    public abstract void postProcess();


    protected abstract void runPostDecodeTasks();


    // TODO_ extend this to also use bigger pages (for now we support only 1024 pages)
    private List<Byte> checkPage(RawHistoryPage page, boolean partial) throws RuntimeException {
        List<Byte> byteList = new ArrayList<Byte>();

        // if (!partial && page.getData().length != 1024 /* page.commandType.getRecordLength() */) {
        // LOG.error("Page size is not correct. Size should be {}, but it was {} instead.", 1024,
        // page.getData().length);
        // // throw exception perhaps
        // return byteList;
        // }

        if (MedtronicUtil.getMedtronicPumpModel() == null) {
            LOG.error("Device Type is not defined.");
            return byteList;
        }

        if (page.getData().length != 1024) {
            return ByteUtil.getListFromByteArray(page.getData());
        } else if (page.isChecksumOK()) {
            return ByteUtil.getListFromByteArray(page.getOnlyData());
        } else {
            return null;
        }
    }


    public List<T> processPageAndCreateRecords(RawHistoryPage rawHistoryPage) {
        return processPageAndCreateRecords(rawHistoryPage, false);
    }


    protected void prepareStatistics() {
        if (!statisticsEnabled)
            return;

        unknownOpCodes = new HashMap<Integer, Integer>();
        mapStatistics = new HashMap<RecordDecodeStatus, Map<String, String>>();

        for (RecordDecodeStatus stat : RecordDecodeStatus.values()) {
            mapStatistics.put(stat, new HashMap<String, String>());
        }
    }


    protected void addToStatistics(MedtronicHistoryEntryInterface pumpHistoryEntry, RecordDecodeStatus status,
                                   Integer opCode) {
        if (!statisticsEnabled)
            return;

        if (opCode != null) {
            if (!unknownOpCodes.containsKey(opCode)) {
                unknownOpCodes.put(opCode, opCode);
            }
            return;
        }

        if (!mapStatistics.get(status).containsKey(pumpHistoryEntry.getEntryTypeName())) {
            mapStatistics.get(status).put(pumpHistoryEntry.getEntryTypeName(), "");
        }
    }


    protected void showStatistics() {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry unknownEntry : unknownOpCodes.entrySet()) {
            StringUtil.appendToStringBuilder(sb, "" + unknownEntry.getKey(), ", ");
        }

        if (isLogEnabled())
            LOG.debug("STATISTICS OF PUMP DECODE");

        if (unknownOpCodes.size() > 0) {
            LOG.warn("Unknown Op Codes: {}", sb.toString());
        }

        for (Map.Entry<RecordDecodeStatus, Map<String, String>> entry : mapStatistics.entrySet()) {
            sb = new StringBuilder();

            if (entry.getKey() != RecordDecodeStatus.OK) {
                if (entry.getValue().size() == 0)
                    continue;

                for (Map.Entry<String, String> entrysub : entry.getValue().entrySet()) {
                    StringUtil.appendToStringBuilder(sb, entrysub.getKey(), ", ");
                }

                String spaces = StringUtils.repeat(" ", 14 - entry.getKey().name().length());

                if (isLogEnabled())
                    LOG.debug("    {}{} - {}. Elements: {}", entry.getKey().name(), spaces, entry.getValue().size(),
                            sb.toString());
            } else {
                if (isLogEnabled())
                    LOG.debug("    {}             - {}", entry.getKey().name(), entry.getValue().size());
            }
        }
    }


    private int getUnsignedByte(byte value) {
        if (value < 0)
            return value + 256;
        else
            return value;
    }


    protected int getUnsignedInt(int value) {
        if (value < 0)
            return value + 256;
        else
            return value;
    }


    public String getFormattedFloat(float value, int decimals) {
        return StringUtil.getFormatedValueUS(value, decimals);
    }


    private List<T> processPageAndCreateRecords(RawHistoryPage rawHistoryPage, boolean partial) {
        List<Byte> dataClear = checkPage(rawHistoryPage, partial);
        List<T> records = createRecords(dataClear);

        for (T record : records) {
            decodeRecord(record);
        }

        runPostDecodeTasks();

        return records;
    }

    protected boolean isLogEnabled() {
        return L.isEnabled(L.PUMPCOMM);
    }

}
