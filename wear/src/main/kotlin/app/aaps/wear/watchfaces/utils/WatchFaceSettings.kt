package app.aaps.wear.watchfaces.utils

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes

/*
 * How a watch face describes its own settings screen, so the fragment that shows it needs to know
 * nothing about that watch face.
 *
 * The split: the watch face decides **which** rows exist, in what order, and later - once filtering
 * by the loaded template arrives - which of them are relevant at all; the fragment knows **how** to
 * turn a row into an androidx Preference. That is why a row carries no behaviour, only what a
 * Preference needs to be built.
 */

/** One row of a watch face's settings screen. */
internal sealed interface WatchFaceSettingRow {

    /** The preference key this row reads and writes. */
    @get:StringRes val key: Int

    /** The row's label. */
    @get:StringRes val title: Int

    /**
     * The key of a toggle this row follows, or null if it always applies. While that toggle is off
     * the row is shown greyed and does not react - it is never hidden. Hiding a row is a different
     * question, answered by whether the watch face lists it here at all.
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

    /** A row that stores nothing and just does something when tapped, e.g. opens a picker. */
    data class Action(
        @StringRes override val key: Int,
        @StringRes override val title: Int,
        @StringRes override val dependencyKey: Int? = null
    ) : WatchFaceSettingRow

    /**
     * A row that opens another screen of rows. The fragment decides *how* to show them - a separate
     * activity today - so nothing about navigation leaks into the watch face's own declaration.
     */
    data class SubScreen(
        @StringRes override val key: Int,
        @StringRes override val title: Int,
        @StringRes override val dependencyKey: Int? = null
    ) : WatchFaceSettingRow

    /**
     * A row that stores nothing and only tells the user something. Its [title] is the message, so it
     * carries no value and is never tappable.
     */
    data class Info(
        @StringRes override val key: Int,
        @StringRes override val title: Int,
        @StringRes override val dependencyKey: Int? = null
    ) : WatchFaceSettingRow
}

/** A watch face that describes its settings screen rather than shipping it as a settings xml. */
internal interface WatchFaceSettings {

    /**
     * The rows of this watch face's settings screen, in the order they are shown.
     *
     * [storedConfiguration] is whatever the watch face last stored to describe itself - for a watch
     * face built from a loaded template, that template. Opaque to the caller, which only fetches it
     * and passes it back: reading it is the watch face's business alone. A watch face may leave out
     * rows that its current configuration gives nothing to act on; one with a fixed layout can
     * ignore the argument entirely.
     *
     * Null when nothing is stored or it could not be read.
     */
    fun settingRows(storedConfiguration: String?): List<WatchFaceSettingRow>

    /**
     * The rows behind the [WatchFaceSettingRow.SubScreen] whose key is [subScreenKey], or an empty
     * list if this watch face declares no such screen.
     *
     * Deliberately **not** filtered by the loaded configuration, unlike [settingRows]: a sub-screen
     * may configure state that is not template-scoped - a complication's type priority belongs to the
     * system's provider binding, which outlives any one template - so hiding a row because the
     * current template does not use it would hide a setting that still applies.
     */
    fun subScreenRows(@StringRes subScreenKey: Int): List<WatchFaceSettingRow> = emptyList()
}
