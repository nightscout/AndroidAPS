package info.nightscout.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.TextView
import androidx.core.view.MenuProvider
import info.nightscout.core.ui.activities.TranslatedDaggerAppCompatActivity
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.stats.DexcomTirCalculator
import info.nightscout.interfaces.stats.TddCalculator
import info.nightscout.interfaces.stats.TirCalculator
import info.nightscout.rx.AapsSchedulers
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.ui.R
import info.nightscout.ui.activityMonitor.ActivityMonitor
import info.nightscout.ui.databinding.ActivityStatsBinding
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
    @Inject lateinit var repository: AppRepository

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

        binding.tdds.addView(TextView(this).apply { text = getString(info.nightscout.core.ui.R.string.tdd) + ": " + rh.gs(R.string.calculation_in_progress) })
        binding.tir.addView(TextView(this).apply { text = getString(info.nightscout.core.ui.R.string.tir) + ": " + rh.gs(R.string.calculation_in_progress) })
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
            OKDialog.showConfirmation(this, rh.gs(R.string.do_you_want_reset_tdd_stats)) {
                handler.post {
                    uel.log(Action.STAT_RESET, Sources.Stats)
                    repository.clearCachedTddData(0)
                    runOnUiThread { recreate() }
                }
            }
        }
        // Add menu items without overriding methods in the Activity
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        onBackPressedDispatcher.onBackPressed()
                        true
                    }

                    else              -> false
                }
        })
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }
}
