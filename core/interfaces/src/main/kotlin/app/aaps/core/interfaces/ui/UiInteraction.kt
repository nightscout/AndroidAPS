package app.aaps.core.interfaces.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import app.aaps.core.interfaces.R

/**
 * Interface to use activities located in different modules
 * usage: startActivity(Intent(context, activityNames.xxxx))
 */
interface UiInteraction {

    /** The main activity of the application. */
    val mainActivity: Class<*>

    /** The activity for displaying error information. */
    val errorHelperActivity: Class<*>

    /** A generic activity that can host a single fragment. */
    val singleFragmentActivity: Class<*>

    /**
     * Display names for units preferences.
     */
    val unitsEntries: Array<CharSequence>

    /**
     * Value names for units preferences.
     */
    val unitsValues: Array<CharSequence>

    /**
     * Show ErrorHelperActivity and start alarm.
     * @param status message inside dialog
     * @param title title of dialog
     * @param soundId sound resource. if == 0 alarm is not started
     */
    fun runAlarm(status: String, title: String, @RawRes soundId: Int = 0)

    /**
     * Triggers an update of the application widget.
     * @param context The context.
     * @param from A string indicating the source of the update request.
     */
    fun updateWidget(context: Context, from: String)

    /**
     * Defines modes for the site rotation dialog.
     */
    enum class SiteMode(val i: Int) {

        /** View existing site change history. */
        VIEW(1),

        /** Record a new site change. */
        EDIT(2)
    }

    /**
     * Defines types of care portal events.
     */
    enum class EventType {

        /** A blood glucose check. */
        BGCHECK,

        /** A CGM sensor insertion. */
        SENSOR_INSERT,

        /** A pump battery change. */
        BATTERY_CHANGE,

        /** A general note. */
        NOTE,

        /** An exercise event. */
        EXERCISE,

        /** A question/prompt. */
        QUESTION,

        /** An announcement. */
        ANNOUNCEMENT
    }

    /**
     * Starts a repeating alarm sound.
     * @param sound The raw resource ID of the sound to play.
     * @param reason A string describing why the alarm is being started.
     */
    fun startAlarm(@RawRes sound: Int, reason: String)

    /**
     * Stops any currently playing alarm.
     * @param reason A string describing why the alarm is being stopped.
     */
    fun stopAlarm(reason: String)

    /** *******************************************************************************
     * Displays a simple alert dialog with a title, a message, and an OK button.
     *
     * @param context The context to use for displaying the dialog.
     * @param title The title of the dialog.
     * @param message The message to display in the dialog. HTML formatted text is accepted.
     * @param onFinish The action to perform when the OK button is clicked or the dialog is dismissed. Run in UI thread.
     */
    fun showOkDialog(context: Context, title: String, message: String, onFinish: (() -> Unit)? = null)

    /** @see showOkDialog */
    fun showOkDialog(context: Context, @StringRes title: Int, @StringRes message: Int, onFinish: (() -> Unit)? = null)

    /**
     * Displays a confirmation dialog with a title, a message, and OK/Cancel buttons.
     *
     * @param context The host activity.
     * @param title The title of the dialog.
     * @param message The message to display in the dialog. HTML formatted text is accepted.
     * @param ok The action to perform when the OK button is clicked. Run in UI thread.
     * @param cancel The action to perform when the Cancel button is clicked or the dialog is dismissed. Run in UI thread.
     * @param icon Add icon if providec
     */
    fun showOkCancelDialog(context: Context, @StringRes title: Int = R.string.confirmation, @StringRes message: Int, ok: (() -> Unit)?, cancel: (() -> Unit)? = null, @DrawableRes icon: Int? = null)

    /** @see showOkCancelDialog */
    fun showOkCancelDialog(context: Context, title: String = context.getString(R.string.confirmation), message: String, ok: (() -> Unit)?, cancel: (() -> Unit)? = null, @DrawableRes icon: Int? = null)

    /**
     * Displays an alert dialog with a title, two messages, a custom icon, and OK/Cancel buttons.
     *
     * @param context The context to use for displaying the dialog.
     * @param title The title of the dialog.
     * @param message The primary message to display in the dialog. HTML formatted text is accepted.
     * @param secondMessage The secondary message to display in the dialog (styled with accent color).
     * @param ok The action to perform when the OK button is clicked. Run in UI thread.
     * @param cancel The action to perform when the Cancel button is clicked or the dialog is dismissed. Run in UI thread.
     * @param icon The drawable resource ID for the custom icon. Defaults to a check icon if null.1
     */
    fun showOkCancelDialog(context: Context, title: String = context.getString(R.string.confirmation), message: String, secondMessage: String, ok: (() -> Unit)?, cancel: (() -> Unit)? = null, @DrawableRes icon: Int? = null)

    /**
     * Displays a dialog with a title, a message, and Yes/No/Cancel buttons.
     *
     * @param context The context to use for displaying the dialog.
     * @param title The title of the dialog.
     * @param message The message to display in the dialog. HTML formatted text is accepted.
     * @param yes The action to perform when the Yes button is clicked. Run in UI thread.
     * @param no The action to perform when the No button is clicked. The dialog is dismissed on cancel. Run in UI thread.
     */
    fun showYesNoCancel(context: Context, @StringRes title: Int, @StringRes message: Int, yes: (() -> Unit)?, no: (() -> Unit)? = null)

    /** @see showYesNoCancel */
    fun showYesNoCancel(context: Context, title: String, message: String, yes: (() -> Unit)?, no: (() -> Unit)? = null)

    /**
     * Displays a error dialog with a title, a message, a warning icon, and Dismiss/optional positive button.
     *
     * @param context The context to use for displaying the dialog.
     * @param title The title of the dialog.
     * @param message The message to display in the dialog. HTML formatted text is accepted.
     * @param positiveButton The resource ID for the positive button text, or -1 if no positive button.
     * @param ok The action to perform when the positive button is clicked. Run in UI thread.
     * @param cancel The action to perform when the Dismiss button is clicked or the dialog is dismissed. Run in UI thread.
     */
    fun showError(context: Context, title: String, message: String, @StringRes positiveButton: Int? = null, ok: (() -> Unit)? = null, cancel: (() -> Unit)? = null)
}
