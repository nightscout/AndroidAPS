import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventBucketedDataCreated
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T

fun AutosensDataStore.loadBgData(to: Long, repository: AppRepository, aapsLogger: AAPSLogger, dateUtil: DateUtil, rxBus: RxBus) {
    synchronized(dataLock) {
        val start = to - T.hours((24 + 10 /* max dia */).toLong()).msecs()
        // there can be some readings with time in close future (caused by wrong time setting on sensor)
        // so add 2 minutes
        bgReadings = repository
            .compatGetBgReadingsDataFromTime(start, to + T.mins(2).msecs(), false)
            .blockingGet()
            .filter { it.value >= 39 }
        aapsLogger.debug(LTag.AUTOSENS) { "BG data loaded. Size: ${bgReadings.size} Start date: ${dateUtil.dateAndTimeString(start)} End date: ${dateUtil.dateAndTimeString(to)}" }
        createBucketedData(aapsLogger, dateUtil)
        rxBus.send(EventBucketedDataCreated())
    }
}

