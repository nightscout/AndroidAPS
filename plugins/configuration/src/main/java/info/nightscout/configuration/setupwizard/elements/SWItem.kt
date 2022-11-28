package info.nightscout.configuration.setupwizard.elements

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.protection.PasswordCheck
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.rx.events.EventSWUpdate
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject

open class SWItem(val injector: HasAndroidInjector, var type: Type) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var passwordCheck: PasswordCheck

    private val eventWorker = Executors.newSingleThreadScheduledExecutor()
    private var scheduledEventPost: ScheduledFuture<*>? = null

    init {
        @Suppress("LeakingThis")
        injector.androidInjector().inject(this)
    }

    @Suppress("unused")
    enum class Type {

        NONE, TEXT, HTML_LINK, BREAK, LISTENER, URL, STRING, NUMBER, DECIMAL_NUMBER, RADIOBUTTON, PLUGIN, BUTTON, FRAGMENT, UNIT_NUMBER, PREFERENCE
    }

    var label: Int? = null
    var comment: Int? = null
    var preferenceId = 0

    open fun label(@StringRes label: Int): SWItem {
        this.label = label
        return this
    }

    fun comment(@StringRes comment: Int): SWItem {
        this.comment = comment
        return this
    }

    open fun save(value: String, updateDelay: Long) {
        sp.putString(preferenceId, value)
        scheduleChange(updateDelay)
    }

    fun generateLayout(view: View): LinearLayout {
        val layout = view as LinearLayout
        layout.removeAllViews()
        return layout
    }

    open fun generateDialog(layout: LinearLayout) {}
    open fun processVisibility() {}

    fun scheduleChange(updateDelay: Long) {
        class PostRunnable : Runnable {

            override fun run() {
                aapsLogger.debug(LTag.CORE, "Firing EventPreferenceChange")
                rxBus.send(EventPreferenceChange(rh.gs(preferenceId)))
                rxBus.send(EventSWUpdate(false))
                scheduledEventPost = null
            }
        }
        // cancel waiting task to prevent sending multiple posts
        scheduledEventPost?.cancel(false)
        val task: Runnable = PostRunnable()
        scheduledEventPost = eventWorker.schedule(task, updateDelay, TimeUnit.SECONDS)
    }

    fun scanForActivity(cont: Context?): AppCompatActivity? {
        return when (cont) {
            null                 -> null
            is AppCompatActivity -> cont
            is ContextWrapper    -> scanForActivity(cont.baseContext)
            else                 -> null
        }
    }
}