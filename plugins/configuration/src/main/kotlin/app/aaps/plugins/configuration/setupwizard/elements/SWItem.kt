package app.aaps.plugins.configuration.setupwizard.elements

import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventSWUpdate
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit
import javax.inject.Inject

open class SWItem @Inject constructor(
    val aapsLogger: AAPSLogger,
    val rh: ResourceHelper,
    val rxBus: RxBus,
    val preferences: Preferences,
    val passwordCheck: PasswordCheck
) {


    private var scheduledEventPost: Disposable? = null

    var label: Int? = null
    var comment: Int? = null
    var preference: PreferenceKey? = null

    open fun label(@StringRes label: Int): SWItem {
        this.label = label
        return this
    }

    fun comment(@StringRes comment: Int): SWItem {
        this.comment = comment
        return this
    }

    open fun save(value: CharSequence, updateDelay: Long) {
        if (preference is StringNonPreferenceKey)
            preferences.put(preference as StringNonPreferenceKey, value.toString())
        if (preference is StringPreferenceKey)
            preferences.put(preference as StringPreferenceKey, value.toString())
        scheduleChange(updateDelay)
    }

    fun generateLayout(view: View): LinearLayout {
        val layout = view as LinearLayout
        layout.removeAllViews()
        return layout
    }

    open fun generateDialog(layout: LinearLayout) {}
    open fun processVisibility(activity: AppCompatActivity) {}

    fun scheduleChange(updateDelay: Long) {
        // cancel waiting task to prevent sending multiple posts
        scheduledEventPost?.dispose()
        scheduledEventPost = Completable
            .timer(updateDelay, TimeUnit.SECONDS)
            .subscribe {
                aapsLogger.debug(LTag.CORE, "Firing EventPreferenceChange")
                rxBus.send(EventPreferenceChange(preference?.key ?: ""))
                rxBus.send(EventSWUpdate(false))
            }
    }
}