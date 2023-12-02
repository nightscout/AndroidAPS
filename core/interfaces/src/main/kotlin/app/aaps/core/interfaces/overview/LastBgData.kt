package app.aaps.core.interfaces.overview

import android.content.Context
import androidx.annotation.ColorInt
import app.aaps.core.data.iob.InMemoryGlucoseValue

/**
 * Provides data about last glucose value for displaying on screen
 */
interface LastBgData {

    /**
     * Get newest glucose value from bucketed data.
     * If there are less than 3 glucose values, bucketed data is not created.
     * In this case take newest [app.aaps.core.data.model.GV] from db and convert it to [InMemoryGlucoseValue]
     *
     * Intended for display on screen only
     *
     * @return newest glucose value
     */
    fun lastBg(): InMemoryGlucoseValue?

    /**
     * Is last value below display low target?
     *
     * @return true if below
     */
    fun isLow(): Boolean

    /**
     * Is last value above display high target?
     *
     * @return true if above
     */
    fun isHigh(): Boolean

    /**
     * Evaluate color based on low - in - high
     *
     * @return color as resource
     */
    @ColorInt fun lastBgColor(context: Context?): Int

    /**
     * Description for a11y
     *
     * @return text
     */
    fun lastBgDescription(): String

    /**
     * @return true if last bg value is less than 9 min old
     */
    fun isActualBg(): Boolean
}