package info.nightscout.androidaps.plugins.pump.omnipod.eros.history;

import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryRecordDao;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryRecordEntity;

public class ErosHistory
{
    private ErosHistoryRecordDao dao;

    public ErosHistory(ErosHistoryRecordDao dao) {
        this.dao = dao;
    }

    public  List<ErosHistoryRecordEntity> getAllErosHistoryRecordsFromTimestamp(long timeInMillis, boolean ascending)     {
        if (ascending){
            return dao.allSinceAsc(timeInMillis);
        }
        else {
            return dao.allSinceDesc(timeInMillis);
        }
    }

    public ErosHistoryRecordEntity findErosHistoryRecordByPumpId(long pumpId)  {
        return dao.byId(pumpId);
    }

    public void create(ErosHistoryRecordEntity historyRecord){
        dao.insert(historyRecord);
    }
}
