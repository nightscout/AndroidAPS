package app.aaps.plugins.aps

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.Spanned
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.utils.HtmlHelper
import app.aaps.plugins.aps.databinding.OpenapsFragmentBinding
import app.aaps.plugins.aps.events.EventOpenAPSUpdateGui
import app.aaps.plugins.aps.events.EventResetOpenAPSGui
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.apache.commons.lang3.ClassUtils
import javax.inject.Inject
import kotlin.reflect.full.declaredMemberProperties

class OpenAPSFragment : DaggerFragment(), MenuProvider {

    private var disposable: CompositeDisposable = CompositeDisposable()

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var dateUtil: DateUtil

    @Suppress("PrivatePropertyName")
    private val ID_MENU_RUN = 503

    private var _binding: OpenapsFragmentBinding? = null
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        OpenapsFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setColorSchemeColors(
            rh.gac(context, android.R.attr.colorPrimaryDark),
            rh.gac(context, android.R.attr.colorPrimary),
            rh.gac(context, com.google.android.material.R.attr.colorSecondary)
        )
        binding.swipeRefresh.setOnRefreshListener {
            binding.lastrun.text = rh.gs(R.string.executing)
            handler.post { activePlugin.activeAPS.invoke("OpenAPS swipe refresh", false) }
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.FIRST, ID_MENU_RUN, 0, rh.gs(R.string.openapsma_run)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        MenuCompat.setGroupDividerEnabled(menu, true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_RUN -> {
                binding.lastrun.text = rh.gs(R.string.executing)
                handler.post { activePlugin.activeAPS.invoke("OpenAPS menu", false) }
                true
            }

            else        -> false
        }

    @Synchronized
    override fun onResume() {
        super.onResume()

        disposable += rxBus
            .toObservable(EventOpenAPSUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventResetOpenAPSGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ resetGUI(it.text) }, fabricPrivacy::logException)

        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    @Synchronized
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("SetTextI18n")
    @Synchronized
    private fun updateGUI() {
        if (_binding == null) return
        val openAPSPlugin = activePlugin.activeAPS
        openAPSPlugin.lastAPSResult?.let { lastAPSResult ->
            binding.result.text = lastAPSResult.rawData().dataClassToHtml()
            binding.request.text = lastAPSResult.resultAsSpanned()
            binding.glucosestatus.text = lastAPSResult.glucoseStatus?.dataClassToHtml(listOf("glucose", "delta", "shortAvgDelta", "longAvgDelta"))
            binding.currenttemp.text = lastAPSResult.currentTemp?.dataClassToHtml()
            binding.iobdata.text = rh.gs(R.string.array_of_elements, lastAPSResult.iobData?.size) + "\n" + lastAPSResult.iob?.dataClassToHtml()
            binding.profile.text = lastAPSResult.oapsProfile?.dataClassToHtml() ?: lastAPSResult.oapsProfileAutoIsf?.dataClassToHtml()
            binding.mealdata.text = lastAPSResult.mealData?.dataClassToHtml()
            binding.scriptdebugdata.text = lastAPSResult.scriptDebug?.joinToString("\n")
            binding.constraints.text = lastAPSResult.inputConstraints?.getReasons()
            binding.autosensdata.text = lastAPSResult.autosensResult?.dataClassToHtml()
            binding.lastrun.text = dateUtil.dateAndTimeString(openAPSPlugin.lastAPSRun)
        }
        binding.swipeRefresh.isRefreshing = false
    }

    @Synchronized
    private fun resetGUI(text: String) {
        if (_binding == null) return
        binding.result.text = text
        binding.glucosestatus.text = ""
        binding.currenttemp.text = ""
        binding.iobdata.text = ""
        binding.profile.text = ""
        binding.mealdata.text = ""
        binding.autosensdata.text = ""
        binding.scriptdebugdata.text = ""
        binding.request.text = ""
        binding.lastrun.text = ""
        binding.swipeRefresh.isRefreshing = false
    }

    private fun Any.dataClassToHtml(): Spanned =
        HtmlHelper.fromHtml(
            StringBuilder().also { sb ->
                this::class.declaredMemberProperties.forEach { property ->
                    property.call(this)?.let { value ->
                        if (ClassUtils.isPrimitiveOrWrapper(value::class.java)) sb.append(property.name.bold(), ": ", value, br)
                        if (value is StringBuilder) sb.append(property.name.bold(), ": ", value.toString(), br)
                    }
                }
            }.toString()
        )

    private fun Any.dataClassToHtml(properties: List<String>): Spanned =
        HtmlHelper.fromHtml(
            StringBuilder().also { sb ->
                properties.forEach { property ->
                    this::class.declaredMemberProperties
                        .firstOrNull { it.name == property }?.call(this)
                        ?.let { value ->
                            if (ClassUtils.isPrimitiveOrWrapper(value::class.java)) sb.append(property.bold(), ": ", value, br)
                            if (value is StringBuilder) sb.append(property.bold(), ": ", value.toString(), br)
                        }
                }
            }.toString()
        )

    private fun String.bold(): String = "<b>$this</b>"
    private val br = "<br>"
}