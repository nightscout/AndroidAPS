package info.nightscout.androidaps.plugins.general.careportal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventCareportalEventChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.careportal.Dialogs.NewNSTreatmentDialog
import info.nightscout.androidaps.plugins.general.overview.StatusLightHandler
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.careportal_fragment.*
import kotlinx.android.synthetic.main.careportal_stats_fragment.*
import javax.inject.Inject

class CareportalFragment : DaggerFragment(), View.OnClickListener {
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var statusLightHandler: StatusLightHandler
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePluginProvider

    private val disposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.careportal_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        careportal_bgcheck.setOnClickListener(this)
        careportal_announcement.setOnClickListener(this)
        careportal_cgmsensorinsert.setOnClickListener(this)
        careportal_cgmsensorstart.setOnClickListener(this)
        careportal_combobolus.setOnClickListener(this)
        careportal_correctionbolus.setOnClickListener(this)
        careportal_carbscorrection.setOnClickListener(this)
        careportal_exercise.setOnClickListener(this)
        careportal_insulincartridgechange.setOnClickListener(this)
        careportal_pumpbatterychange.setOnClickListener(this)
        careportal_mealbolus.setOnClickListener(this)
        careportal_note.setOnClickListener(this)
        careportal_profileswitch.setOnClickListener(this)
        careportal_pumpsitechange.setOnClickListener(this)
        careportal_question.setOnClickListener(this)
        careportal_snackbolus.setOnClickListener(this)
        careportal_tempbasalend.setOnClickListener(this)
        careportal_tempbasalstart.setOnClickListener(this)
        careportal_openapsoffline.setOnClickListener(this)
        careportal_temporarytarget.setOnClickListener(this)

        val profileStore = activePlugin.activeProfileInterface.profile
        if (profileStore == null) {
            profileview_noprofile.visibility = View.VISIBLE
            careportal_buttons.visibility = View.GONE
        } else {
            profileview_noprofile.visibility = View.GONE
            careportal_buttons.visibility = View.VISIBLE
        }
    }

    @Synchronized override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventCareportalEventChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }) { fabricPrivacy.logException(it) }
        )
        updateGUI()
    }

    @Synchronized override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    override fun onClick(view: View) {
        val BGCHECK = OptionsToShow(R.id.careportal_bgcheck, R.string.careportal_bgcheck).date().bg()
        val SNACKBOLUS = OptionsToShow(R.id.careportal_snackbolus, R.string.careportal_snackbolus).date().bg().insulin().carbs().prebolus()
        val MEALBOLUS = OptionsToShow(R.id.careportal_mealbolus, R.string.careportal_mealbolus).date().bg().insulin().carbs().prebolus()
        val CORRECTIONBOLUS = OptionsToShow(R.id.careportal_correctionbolus, R.string.careportal_correctionbolus).date().bg().insulin().carbs().prebolus()
        val CARBCORRECTION = OptionsToShow(R.id.careportal_carbscorrection, R.string.careportal_carbscorrection).date().bg().carbs()
        val COMBOBOLUS = OptionsToShow(R.id.careportal_combobolus, R.string.careportal_combobolus).date().bg().insulin().carbs().prebolus().duration().split()
        val ANNOUNCEMENT = OptionsToShow(R.id.careportal_announcement, R.string.careportal_announcement).date().bg()
        val NOTE = OptionsToShow(R.id.careportal_note, R.string.careportal_note).date().bg().duration()
        val QUESTION = OptionsToShow(R.id.careportal_question, R.string.careportal_question).date().bg()
        val EXERCISE = OptionsToShow(R.id.careportal_exercise, R.string.careportal_exercise).date().duration()
        val SITECHANGE = OptionsToShow(R.id.careportal_pumpsitechange, R.string.careportal_pumpsitechange).date().bg()
        val SENSORSTART = OptionsToShow(R.id.careportal_cgmsensorstart, R.string.careportal_cgmsensorstart).date().bg()
        val SENSORCHANGE = OptionsToShow(R.id.careportal_cgmsensorinsert, R.string.careportal_cgmsensorinsert).date().bg()
        val INSULINCHANGE = OptionsToShow(R.id.careportal_insulincartridgechange, R.string.careportal_insulincartridgechange).date().bg()
        val PUMPBATTERYCHANGE = OptionsToShow(R.id.careportal_pumpbatterychange, R.string.careportal_pumpbatterychange).date().bg()
        val TEMPBASALSTART = OptionsToShow(R.id.careportal_tempbasalstart, R.string.careportal_tempbasalstart).date().bg().duration().percent().absolute()
        val TEMPBASALEND = OptionsToShow(R.id.careportal_tempbasalend, R.string.careportal_tempbasalend).date().bg()
        val PROFILESWITCH = OptionsToShow(R.id.careportal_profileswitch, R.string.careportal_profileswitch).date().duration().profile()
        val OPENAPSOFFLINE = OptionsToShow(R.id.careportal_openapsoffline, R.string.careportal_openapsoffline).date().duration()
        val TEMPTARGET = OptionsToShow(R.id.careportal_temporarytarget, R.string.careportal_temporarytarget).date().duration().tempTarget()

        val newDialog = NewNSTreatmentDialog()
        when (view.id) {
            R.id.careportal_bgcheck                -> newDialog.setOptions(BGCHECK, R.string.careportal_bgcheck)
            R.id.careportal_announcement           -> newDialog.setOptions(ANNOUNCEMENT, R.string.careportal_announcement)
            R.id.careportal_cgmsensorinsert        -> newDialog.setOptions(SENSORCHANGE, R.string.careportal_cgmsensorinsert)
            R.id.careportal_cgmsensorstart         -> newDialog.setOptions(SENSORSTART, R.string.careportal_cgmsensorstart)
            R.id.careportal_combobolus             -> newDialog.setOptions(COMBOBOLUS, R.string.careportal_combobolus)
            R.id.careportal_correctionbolus        -> newDialog.setOptions(CORRECTIONBOLUS, R.string.careportal_correctionbolus)
            R.id.careportal_carbscorrection        -> newDialog.setOptions(CARBCORRECTION, R.string.careportal_carbscorrection)
            R.id.careportal_exercise               -> newDialog.setOptions(EXERCISE, R.string.careportal_exercise)
            R.id.careportal_insulincartridgechange -> newDialog.setOptions(INSULINCHANGE, R.string.careportal_insulincartridgechange)
            R.id.careportal_pumpbatterychange      -> newDialog.setOptions(PUMPBATTERYCHANGE, R.string.careportal_pumpbatterychange)
            R.id.careportal_mealbolus              -> newDialog.setOptions(MEALBOLUS, R.string.careportal_mealbolus)
            R.id.careportal_note                   -> newDialog.setOptions(NOTE, R.string.careportal_note)
            R.id.careportal_profileswitch          -> newDialog.setOptions(PROFILESWITCH, R.string.careportal_profileswitch)
            R.id.careportal_pumpsitechange         -> newDialog.setOptions(SITECHANGE, R.string.careportal_pumpsitechange)
            R.id.careportal_question               -> newDialog.setOptions(QUESTION, R.string.careportal_question)
            R.id.careportal_snackbolus             -> newDialog.setOptions(SNACKBOLUS, R.string.careportal_snackbolus)
            R.id.careportal_tempbasalstart         -> newDialog.setOptions(TEMPBASALSTART, R.string.careportal_tempbasalstart)
            R.id.careportal_tempbasalend           -> newDialog.setOptions(TEMPBASALEND, R.string.careportal_tempbasalend)
            R.id.careportal_openapsoffline         -> newDialog.setOptions(OPENAPSOFFLINE, R.string.careportal_openapsoffline)
            R.id.careportal_temporarytarget        -> newDialog.setOptions(TEMPTARGET, R.string.careportal_temporarytarget)
        }
        fragmentManager?.let {
            NewNSTreatmentDialog().show(it, "CareportalFragment")
        }
    }

    private fun updateGUI() {
        statusLightHandler.updateStatusLights(careportal_canulaage, careportal_insulinage, null, careportal_sensorage, careportal_pbage, null)
    }
}