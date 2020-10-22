package info.nightscout.androidaps.setupwizard

import android.annotation.SuppressLint
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.events.EventStatus
import info.nightscout.androidaps.setupwizard.elements.SWItem
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable

class SWEventListener constructor(
    injector: HasAndroidInjector,
    clazz: Class<out EventStatus>
) : SWItem(injector, Type.LISTENER) {

    private val disposable = CompositeDisposable()
    private var textLabel = 0
    private var status = ""
    private var textView: TextView? = null
    private var visibilityValidator: SWValidator? = null

    // TODO: Adrian how to clear disposable in this case?
    init {
        disposable.add(rxBus
            .toObservable(clazz)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { event: Any ->
                status = (event as EventStatus).getStatus(resourceHelper)
                @SuppressLint("SetTextI18n")
                textView?.text = (if (textLabel != 0) resourceHelper.gs(textLabel) else "") + " " + status
            }
        )
    }

    override fun label(label: Int): SWEventListener {
        textLabel = label
        return this
    }

    fun initialStatus(status: String): SWEventListener {
        this.status = status
        return this
    }

    fun visibility(visibilityValidator: SWValidator): SWEventListener {
        this.visibilityValidator = visibilityValidator
        return this
    }

    @SuppressLint("SetTextI18n")
    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        textView = TextView(context)
        textView?.id = View.generateViewId()
        textView?.text = (if (textLabel != 0) resourceHelper.gs(textLabel) else "") + " " + status
        layout.addView(textView)
    }

    override fun processVisibility() {
        if (visibilityValidator != null && !visibilityValidator!!.isValid) textView?.visibility = View.GONE else textView?.visibility = View.VISIBLE
    }
}