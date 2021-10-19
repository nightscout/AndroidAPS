package info.nightscout.androidaps.plugins.pump.omnipod.eros.history;

import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryRecordDao;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryRecordEntity;
import io.reactivex.Single;

public class ErosHistory
{
    private final ErosHistoryRecordDao dao;

    public ErosHistory(ErosHistoryRecordDao dao) {
        this.dao = dao;
    }

    public List<ErosHistoryRecordEntity> getAllErosHistoryRecordsFromTimestamp(long timeInMillis)     {
        return dao.allSinceAsc(timeInMillis).blockingGet();

    }

    public ErosHistoryRecordEntity findErosHistoryRecordByPumpId(long pumpId)  {
        Single<ErosHistoryRecordEntity> entity = dao.byId(pumpId);
        return (entity == null) ? null: entity.blockingGet();
    }

    public void create(ErosHistoryRecordEntity historyRecord){
        // no need for rowId, but lose warnings in IDE and make sure transaction is completed.
        long rowId = Single.just(dao.insert(historyRecord)).blockingGet();
    }
}
