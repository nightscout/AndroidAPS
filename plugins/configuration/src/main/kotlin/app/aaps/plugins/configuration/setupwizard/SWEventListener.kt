package app.aaps.plugins.configuration.setupwizard

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.events.EventStatus
import app.aaps.plugins.configuration.setupwizard.elements.SWItem
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class SWEventListener(
    injector: HasAndroidInjector,
    clazz: Class<out EventStatus>
) : SWItem(injector, Type.LISTENER) {

    private val disposable = CompositeDisposable()
    private var textLabel = 0
    private var status = ""
    private var textView: TextView? = null
    private var visibilityValidator: (() -> Boolean)? = null

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var context: Context

    // TODO: Adrian how to clear disposable in this case?
    init {
        disposable += rxBus
            .toObservable(clazz)
            .observeOn(aapsSchedulers.main)
            .subscribe { event: Any ->
                status = (event as EventStatus).getStatus(context)
                @SuppressLint("SetTextI18n")
                textView?.text = (if (textLabel != 0) rh.gs(textLabel) else "") + " " + status
            }
    }

    override fun label(label: Int): SWEventListener {
        textLabel = label
        return this
    }

    fun initialStatus(status: String): SWEventListener {
        this.status = status
        return this
    }

    fun visibility(visibilityValidator: () -> Boolean): SWEventListener {
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
        if (visibilityValidator?.invoke() == false) textView?.visibility = View.GONE else textView?.visibility = View.VISIBLE
    }
}