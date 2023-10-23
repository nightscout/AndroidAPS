package app.aaps.plugins.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.plugins.automation.databinding.AutomationDialogEditTriggerBinding
import app.aaps.plugins.automation.events.EventAutomationUpdateTrigger
import app.aaps.plugins.automation.events.EventTriggerChanged
import app.aaps.plugins.automation.events.EventTriggerClone
import app.aaps.plugins.automation.events.EventTriggerRemove
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerDummy
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import javax.inject.Inject

class EditTriggerDialog : BaseDialog() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    private var disposable: CompositeDisposable = CompositeDisposable()

    private var triggers: TriggerConnector? = null

    private var _binding: AutomationDialogEditTriggerBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            bundle.getString("trigger")?.let { triggers = TriggerDummy(injector).instantiate(JSONObject(it)) as TriggerConnector }
        }

        onCreateViewGeneral()
        _binding = AutomationDialogEditTriggerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        disposable += rxBus
            .toObservable(EventTriggerChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           binding.layoutTrigger.removeAllViews()
                           triggers?.generateDialog(binding.layoutTrigger)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTriggerRemove::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           findParent(triggers, it.trigger)?.list?.remove(it.trigger)
                           binding.layoutTrigger.removeAllViews()
                           triggers?.generateDialog(binding.layoutTrigger)
                       }, fabricPrivacy::logException)

        disposable += rxBus
            .toObservable(EventTriggerClone::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           findParent(triggers, it.trigger)?.list?.add(it.trigger.duplicate())
                           binding.layoutTrigger.removeAllViews()
                           triggers?.generateDialog(binding.layoutTrigger)
                       }, fabricPrivacy::logException)

        // display root trigger
        triggers?.generateDialog(binding.layoutTrigger)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    override fun submit(): Boolean {
        triggers?.let { trigger -> rxBus.send(EventAutomationUpdateTrigger(trigger)) }
        return true
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        triggers?.let { savedInstanceState.putString("trigger", it.toJSON()) }
    }

    private fun findParent(where: Trigger?, what: Trigger): TriggerConnector? {
        if (where == null) return null
        if (where is TriggerConnector) {
            for (i in where.list) {
                if (i == what) return where
                if (i is TriggerConnector) {
                    val found = findParent(i, what)
                    if (found != null) return found
                }
            }
        }
        return null
    }
}