package app.aaps.wear.watchfaces.utils

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes

/*
 * How a watch face describes its own settings screen, so the fragment showing it needs to know
 * nothing about that watch face: the watch face decides which rows exist and in what order, the
 * fragment knows how to turn a row into an androidx Preference. A row therefore carries no
 * behaviour, only what a Preference needs to be built.
 */

/** One row of a watch face's settings screen. */
internal sealed interface WatchFaceSettingRow {

    /** The preference key this row reads and writes. */
    @get:StringRes val key: Int

    /** The row's label. */
    @get:StringRes val title: Int

    /**
     * Key of a toggle this row follows, or null if it always applies. While that toggle is off the row
     * is greyed and inert - never hidden. Whether a row appears at all is decided by the watch face
     * listing it or not.
     */
    @get:StringRes val dependencyKey: Int?

    /** An on/off row. */
    data class Toggle(
        @StringRes override val key: Int,
        @StringRes override val title: Int,
        val defaultValue: Boolean,
        @StringRes override val dependencyKey: Int? = null
    ) : WatchFaceSettingRow

    /** A row offering a fixed list of values, showing the chosen one as its summary. */
    data class Choice(
        @StringRes override val key: Int,
        @StringRes override val title: Int,
        @ArrayRes val entries: Int,
        @ArrayRes val entryValues: Int,
        val defaultValue: String,
        @StringRes override val dependencyKey: Int? = null
    ) : WatchFaceSettingRow

    /** A row that stores nothing and just acts when tapped, e.g. opens a picker. */
    data class Action(
        @StringRes override val key: Int,
        @StringRes override val title: Int,
        @StringRes override val dependencyKey: Int? = null
    ) : WatchFaceSettingRow

    /** A row that opens another screen of rows; the fragment decides how to present it. */
    data class SubScreen(
        @StringRes override val key: Int,
        @StringRes override val title: Int,
        @StringRes override val dependencyKey: Int? = null
    ) : WatchFaceSettingRow

    /** A row that stores nothing and only shows a message, carried in its [title]. */
    data class Info(
        @StringRes override val key: Int,
        @StringRes override val title: Int,
        @StringRes override val dependencyKey: Int? = null
    ) : WatchFaceSettingRow
}

/** A watch face that describes its settings screen rather than shipping it as a settings xml. */
internal interface WatchFaceSettings {

    /**
     * The rows of this watch face's settings screen, in display order.
     *
     * [storedConfiguration] is whatever the watch face last stored to describe itself, opaque to the
     * caller - which only fetches it and hands it back. A watch face may leave out rows its current
     * configuration gives nothing to act on; one with a fixed layout can ignore the argument. Null
     * when nothing is stored or it could not be read.
     */
    fun settingRows(storedConfiguration: String?): List<WatchFaceSettingRow>

    /**
     * The rows behind the [WatchFaceSettingRow.SubScreen] with key [subScreenKey], or empty if this
     * watch face declares no such screen.
     *
     * Deliberately not filtered by the loaded configuration, unlike [settingRows]: a sub-screen may
     * configure state that outlives any one template - a complication's type priority belongs to the
     * system's provider binding - so filtering would hide a setting that still applies.
     */
    fun subScreenRows(@StringRes subScreenKey: Int): List<WatchFaceSettingRow> = emptyList()
}
