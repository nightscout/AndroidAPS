package app.aaps.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.widget.TextView
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.stats.DexcomTirCalculator
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.ui.R
import app.aaps.ui.activityMonitor.ActivityMonitor
import app.aaps.ui.databinding.ActivityStatsBinding
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class StatsActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var tirCalculator: TirCalculator
    @Inject lateinit var dexcomTirCalculator: DexcomTirCalculator
    @Inject lateinit var activityMonitor: ActivityMonitor
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var persistenceLayer: PersistenceLayer

    private lateinit var binding: ActivityStatsBinding
    private val disposable = CompositeDisposable()
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(R.string.statistics)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.tdds.addView(TextView(this).apply { text = getString(app.aaps.core.ui.R.string.tdd) + ": " + rh.gs(R.string.calculation_in_progress) })
        binding.tir.addView(TextView(this).apply { text = getString(app.aaps.core.ui.R.string.tir) + ": " + rh.gs(R.string.calculation_in_progress) })
        binding.activity.addView(TextView(this).apply { text = getString(R.string.activity_monitor) + ": " + rh.gs(R.string.calculation_in_progress) })

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

        binding.resetActivity.setOnClickListener {
            OKDialog.showConfirmation(this, rh.gs(R.string.do_you_want_reset_stats)) {
                uel.log(Action.STAT_RESET, Sources.Stats)
                activityMonitor.reset()
                recreate()
            }
        }
        binding.resetTdd.setOnClickListener {
            OKDialog.showConfirmation(this, rh.gs(R.string.do_you_want_recalculate_tdd_stats)) {
                handler.post {
                    uel.log(Action.STAT_RESET, Sources.Stats)
                    persistenceLayer.clearCachedTddData(0)
                    runOnUiThread { recreate() }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.resetActivity.setOnClickListener(null)
        binding.resetTdd.setOnClickListener(null)
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }
}
