package info.nightscout.pump.common.driver.history

import info.nightscout.pump.common.defs.PumpHistoryEntryGroup
import java.util.Calendar
import java.util.GregorianCalendar

abstract class PumpHistoryDataProviderAbstract : PumpHistoryDataProvider {

    override fun getInitialData(): List<PumpHistoryEntry> {
        return getData(getInitialPeriod())
    }

    override fun getSpinnerWidthInPixels(): Int {
        return 150
    }

    protected fun getStartingTimeForData(period: PumpHistoryPeriod): Long {
        val gregorianCalendar = GregorianCalendar()

        if (!period.isHours) {
            gregorianCalendar.set(Calendar.HOUR_OF_DAY, 0)
            gregorianCalendar.set(Calendar.MINUTE, 0)
            gregorianCalendar.set(Calendar.SECOND, 0)
            gregorianCalendar.set(Calendar.MILLISECOND, 0)
        }

        when (period) {
            PumpHistoryPeriod.TODAY         -> return gregorianCalendar.timeInMillis
            PumpHistoryPeriod.ALL           -> return 0L
            PumpHistoryPeriod.LAST_2_DAYS   -> gregorianCalendar.add(Calendar.DAY_OF_MONTH, -1)
            PumpHistoryPeriod.LAST_4_DAYS   -> gregorianCalendar.add(Calendar.DAY_OF_MONTH, -3)
            PumpHistoryPeriod.LAST_WEEK     -> gregorianCalendar.add(Calendar.WEEK_OF_YEAR, -1)
            PumpHistoryPeriod.LAST_MONTH    -> gregorianCalendar.add(Calendar.MONTH, -1)
            PumpHistoryPeriod.LAST_HOUR     -> gregorianCalendar.add(Calendar.HOUR_OF_DAY, -1)
            PumpHistoryPeriod.LAST_3_HOURS  -> gregorianCalendar.add(Calendar.HOUR_OF_DAY, -3)
            PumpHistoryPeriod.LAST_6_HOURS  -> gregorianCalendar.add(Calendar.HOUR_OF_DAY, -6)
            PumpHistoryPeriod.LAST_12_HOURS -> gregorianCalendar.add(Calendar.HOUR_OF_DAY, -12)
        }

        return gregorianCalendar.timeInMillis
    }

    override fun isItemInSelection(itemGroup: PumpHistoryEntryGroup, targetGroup: PumpHistoryEntryGroup): Boolean {
        return itemGroup === targetGroup
    }

}