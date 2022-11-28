package info.nightscout.configuration.setupwizard

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dagger.android.HasAndroidInjector
import info.nightscout.configuration.setupwizard.elements.SWItem
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.events.EventStatus
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class SWEventListener constructor(
    injector: HasAndroidInjector,
    clazz: Class<out EventStatus>
) : SWItem(injector, Type.LISTENER) {

    private val disposable = CompositeDisposable()
    private var textLabel = 0
    private var status = ""
    private var textView: TextView? = null
    private var visibilityValidator: SWValidator? = null

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var context: Context

    // TODO: Adrian how to clear disposable in this case?
    init {
        disposable.add(rxBus
            .toObservable(clazz)
            .observeOn(aapsSchedulers.main)
            .subscribe { event: Any ->
                status = (event as EventStatus).getStatus(context)
                @SuppressLint("SetTextI18n")
                textView?.text = (if (textLabel != 0) rh.gs(textLabel) else "") + " " + status
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
        textView?.text = (if (textLabel != 0) rh.gs(textLabel) else "") + " " + status
        layout.addView(textView)
    }

    override fun processVisibility() {
        if (visibilityValidator != null && !visibilityValidator!!.isValid) textView?.visibility = View.GONE else textView?.visibility = View.VISIBLE
    }
}