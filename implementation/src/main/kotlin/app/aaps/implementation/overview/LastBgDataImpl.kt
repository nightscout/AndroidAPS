package app.aaps.implementation.overview

import android.content.Context
import androidx.annotation.ColorInt
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.fromGv
import app.aaps.core.objects.extensions.valueToUnits
import dagger.Reusable
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@Reusable
class LastBgDataImpl @Inject constructor(
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val preferences: Preferences,
    private val iobCobCalculator: IobCobCalculator
) : LastBgData {

    override fun lastBg(): InMemoryGlucoseValue? =
        iobCobCalculator.ads.bucketedData?.firstOrNull()
            ?: runBlocking { persistenceLayer.getLastGlucoseValue() }?.let { InMemoryGlucoseValue.fromGv(it) }

    override fun isLow(): Boolean =
        lastBg()?.let { lastBg ->
            lastBg.valueToUnits(profileFunction.getUnits()) < preferences.get(UnitDoubleKey.OverviewLowMark)
        } == true

    override fun isHigh(): Boolean =
        lastBg()?.let { lastBg ->
            lastBg.valueToUnits(profileFunction.getUnits()) > preferences.get(UnitDoubleKey.OverviewHighMark)
        } == true

    @ColorInt
    override fun lastBgColor(context: Context?): Int =
        when {
            isLow()  -> rh.gac(context, app.aaps.core.ui.R.attr.bgLow)
            isHigh() -> rh.gac(context, app.aaps.core.ui.R.attr.highColor)
            else     -> rh.gac(context, app.aaps.core.ui.R.attr.bgInRange)
        }

    override fun lastBgDescription(): String =
        when {
            isLow()  -> rh.gs(app.aaps.core.ui.R.string.a11y_low)
            isHigh() -> rh.gs(app.aaps.core.ui.R.string.a11y_high)
            else     -> rh.gs(app.aaps.core.ui.R.string.a11y_inrange)
        }

    override fun isActualBg(): Boolean =
        lastBg()?.let { lastBg ->
            lastBg.timestamp > dateUtil.now() - T.mins(9).msecs()
        } == true
}