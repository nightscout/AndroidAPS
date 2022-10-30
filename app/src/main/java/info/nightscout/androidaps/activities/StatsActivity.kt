package info.nightscout.androidaps.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.databinding.ActivityStatsBinding
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.utils.ActivityMonitor
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.stats.DexcomTirCalculator
import info.nightscout.androidaps.utils.stats.TddCalculator
import info.nightscout.androidaps.utils.stats.TirCalculator
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class StatsActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var tirCalculator: TirCalculator
    @Inject lateinit var dexcomTirCalculator: DexcomTirCalculator
    @Inject lateinit var activityMonitor: ActivityMonitor
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    private lateinit var binding: ActivityStatsBinding
    private val disposable = CompositeDisposable()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tdds.addView(TextView(this).apply { text = getString(R.string.tdd) + ": " + rh.gs(R.string.calculation_in_progress) })
        binding.tir.addView(TextView(this).apply { text = getString(R.string.tir) + ": " + rh.gs(R.string.calculation_in_progress) })
        binding.activity.addView(TextView(this).apply { text = getString(R.string.activitymonitor) + ": " + rh.gs(R.string.calculation_in_progress) })

        disposable += Single.fromCallable { tddCalculator.stats(this) }
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           binding.tdds.removeAllViews()
                           binding.tdds.addView(it)
                       }, fabricPrivacy::logException)
        disposable += Single.fromCallable { tirCalculator.stats(this) }
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           binding.tir.removeAllViews()
                           binding.tir.addView(it)
                       }, fabricPrivacy::logException)
        disposable += Single.fromCallable { dexcomTirCalculator.stats(this) }
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           binding.dexcomTir.removeAllViews()
                           binding.dexcomTir.addView(it)
                       }, fabricPrivacy::logException)
        disposable += Single.fromCallable { activityMonitor.stats(this) }
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           binding.activity.removeAllViews()
                           binding.activity.addView(it)
                       }, fabricPrivacy::logException)

        binding.ok.setOnClickListener { finish() }
        binding.reset.setOnClickListener {
            OKDialog.showConfirmation(this, rh.gs(R.string.doyouwantresetstats)) {
                uel.log(Action.STAT_RESET, Sources.Stats)
                activityMonitor.reset()
                recreate()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }
}
