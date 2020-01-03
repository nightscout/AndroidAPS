package info.nightscout.androidaps.setupwizard

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import info.nightscout.androidaps.events.EventStatus
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.setupwizard.elements.SWItem
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable

class SWEventListener constructor(
    private val resourceHelper: ResourceHelper,
    rxBus: RxBusWrapper,
    clazz: Class<out EventStatus>
) : SWItem(Type.LISTENER) {

    private val disposable = CompositeDisposable()
    private var textLabel = 0
    private var status = ""
    var textView: TextView? = null

    // TODO: Adrian how to clear disposable in this case?
    init {
        disposable.add(rxBus
            .toObservable(clazz)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { event: Any ->
                status = (event as EventStatus).getStatus(resourceHelper)
                textView?.text = (if (textLabel != 0) resourceHelper.gs(textLabel) else "") + " " + status
            }
        )
    }

    override fun label(newLabel: Int): SWEventListener {
        textLabel = newLabel
        return this
    }

    fun initialStatus(status: String): SWEventListener {
        this.status = status
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        textView = TextView(context)
        textView?.id = View.generateViewId()
        textView?.text = (if (textLabel != 0) resourceHelper.gs(textLabel) else "") + " " + status
        layout.addView(textView)
    }
}