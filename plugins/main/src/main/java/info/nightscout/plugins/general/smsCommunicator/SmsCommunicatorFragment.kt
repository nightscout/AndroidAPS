package info.nightscout.plugins.general.smsCommunicator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.core.utils.HtmlHelper
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.smsCommunicator.Sms
import info.nightscout.interfaces.smsCommunicator.SmsCommunicator
import info.nightscout.plugins.databinding.SmscommunicatorFragmentBinding
import info.nightscout.plugins.general.smsCommunicator.events.EventSmsCommunicatorUpdateGui
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.Collections
import javax.inject.Inject
import kotlin.math.max

class SmsCommunicatorFragment : DaggerFragment() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var smsCommunicator: SmsCommunicator
    @Inject lateinit var dateUtil: DateUtil

    private val disposable = CompositeDisposable()

    private var _binding: SmscommunicatorFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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

    private fun updateGui() {
        if (_binding == null) return
        class CustomComparator : Comparator<Sms> {

            override fun compare(object1: Sms, object2: Sms): Int {
                return (object1.date - object2.date).toInt()
            }
        }
        Collections.sort(smsCommunicator.messages, CustomComparator())
        val messagesToShow = 40
        val start = max(0, smsCommunicator.messages.size - messagesToShow)
        var logText = ""
        for (x in start until smsCommunicator.messages.size) {
            val sms = smsCommunicator.messages[x]
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