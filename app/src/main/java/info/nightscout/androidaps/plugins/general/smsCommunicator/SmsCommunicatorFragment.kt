package info.nightscout.androidaps.plugins.general.smsCommunicator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.databinding.SmscommunicatorFragmentBinding
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.smsCommunicator.events.EventSmsCommunicatorUpdateGui
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import io.reactivex.rxkotlin.plusAssign
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import java.util.*
import javax.inject.Inject
import kotlin.math.max

class SmsCommunicatorFragment : DaggerFragment() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    @Inject lateinit var dateUtil: DateUtil

    private val disposable = CompositeDisposable()

    private var _binding: SmscommunicatorFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = SmscommunicatorFragmentBinding.inflate(inflater, container, false)
        return binding.root

    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventSmsCommunicatorUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun updateGui() {
        if (_binding == null) return
        class CustomComparator : Comparator<Sms> {

            override fun compare(object1: Sms, object2: Sms): Int {
                return (object1.date - object2.date).toInt()
            }
        }
        Collections.sort(smsCommunicatorPlugin.messages, CustomComparator())
        val messagesToShow = 40
        val start = max(0, smsCommunicatorPlugin.messages.size - messagesToShow)
        var logText = ""
        for (x in start until smsCommunicatorPlugin.messages.size) {
            val sms = smsCommunicatorPlugin.messages[x]
            when {
                sms.ignored  -> {
                    logText += dateUtil.timeString(sms.date) + " &lt;&lt;&lt; " + "░ " + sms.phoneNumber + " <b>" + sms.text + "</b><br>"
                }

                sms.received -> {
                    logText += dateUtil.timeString(sms.date) + " &lt;&lt;&lt; " + (if (sms.processed) "● " else "○ ") + sms.phoneNumber + " <b>" + sms.text + "</b><br>"
                }

                sms.sent     -> {
                    logText += dateUtil.timeString(sms.date) + " &gt;&gt;&gt; " + (if (sms.processed) "● " else "○ ") + sms.phoneNumber + " <b>" + sms.text + "</b><br>"
                }
            }
        }
        binding.log.text = HtmlHelper.fromHtml(logText)
    }
}