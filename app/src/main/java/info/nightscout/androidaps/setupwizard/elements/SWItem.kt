package info.nightscout.androidaps.setupwizard.elements

import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.setupwizard.events.EventSWUpdate
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject

open class SWItem(val injector: HasAndroidInjector, var type: Type) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var sp: SP

    private val eventWorker = Executors.newSingleThreadScheduledExecutor()
    private var scheduledEventPost: ScheduledFuture<*>? = null

    init {
        @Suppress("LeakingThis")
        injector.androidInjector().inject(this)
    }

    enum class Type {
        NONE, TEXT, HTML_LINK, BREAK, LISTENER, URL, STRING, NUMBER, DECIMAL_NUMBER, RADIOBUTTON, PLUGIN, BUTTON, FRAGMENT, UNIT_NUMBER
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
                rxBus.send(EventPreferenceChange(resourceHelper, preferenceId))
                rxBus.send(EventSWUpdate(false))
                scheduledEventPost = null
            }
        }
        // cancel waiting task to prevent sending multiple posts
        scheduledEventPost?.cancel(false)
        val task: Runnable = PostRunnable()
        scheduledEventPost = eventWorker.schedule(task, updateDelay, TimeUnit.SECONDS)
    }
}