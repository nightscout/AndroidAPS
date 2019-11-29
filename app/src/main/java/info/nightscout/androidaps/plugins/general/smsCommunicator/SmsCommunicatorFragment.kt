package info.nightscout.androidaps.plugins.general.smsCommunicator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.bus.RxBus.toObservable
import info.nightscout.androidaps.plugins.general.smsCommunicator.events.EventSmsCommunicatorUpdateGui
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.smscommunicator_fragment.*
import java.util.*
import kotlin.math.max

class SmsCommunicatorFragment : Fragment() {
    private val disposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.smscommunicator_fragment, container, false)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(toObservable(EventSmsCommunicatorUpdateGui::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGui() }) { FabricPrivacy.logException(it) }
        )
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    fun updateGui() {
        class CustomComparator : Comparator<Sms> {
            override fun compare(object1: Sms, object2: Sms): Int {
                return (object1.date - object2.date).toInt()
            }
        }
        Collections.sort(SmsCommunicatorPlugin.messages, CustomComparator())
        val messagesToShow = 40
        val start = max(0, SmsCommunicatorPlugin.messages.size - messagesToShow)
        var logText = ""
        for (x in start until SmsCommunicatorPlugin.messages.size) {
            val sms = SmsCommunicatorPlugin.messages[x]
            when {
                sms.ignored -> {
                    logText += DateUtil.timeString(sms.date) + " &lt;&lt;&lt; " + "░ " + sms.phoneNumber + " <b>" + sms.text + "</b><br>"
                }
                sms.received -> {
                    logText += DateUtil.timeString(sms.date) + " &lt;&lt;&lt; " + (if (sms.processed) "● " else "○ ") + sms.phoneNumber + " <b>" + sms.text + "</b><br>"
                }
                sms.sent -> {
                    logText += DateUtil.timeString(sms.date) + " &gt;&gt;&gt; " + (if (sms.processed) "● " else "○ ") + sms.phoneNumber + " <b>" + sms.text + "</b><br>"
                }
            }
        }
        smscommunicator_log?.text = HtmlHelper.fromHtml(logText)
    }
}