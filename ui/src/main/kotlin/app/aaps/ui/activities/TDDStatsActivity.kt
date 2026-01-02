package app.aaps.ui.activities

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import app.aaps.core.data.model.TDD
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.ui.databinding.ActivityTddStatsBinding
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

class TDDStatsActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger

    private lateinit var binding: ActivityTddStatsBinding
    private val disposable = CompositeDisposable()

    private lateinit var tbb: String
    private var magicNumber = 0.0
    private var decimalFormat: DecimalFormat = DecimalFormat("0.000")
    private var historyList: MutableList<TDD> = mutableListOf()
    private var dummies: MutableList<TDD> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTddStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(app.aaps.core.ui.R.string.tdd)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        binding.connectionStatus.visibility = View.GONE
        binding.message.visibility = View.GONE
        binding.totalBaseBasal2.isEnabled = false
        binding.totalBaseBasal2.isClickable = false
        binding.totalBaseBasal2.isFocusable = false
        binding.totalBaseBasal2.inputType = 0
        tbb = preferences.get(StringNonKey.TotalBaseBasal)
        val profile = profileFunction.getProfile()
        if (profile != null) {
            val cppTBB = profile.baseBasalSum()
            tbb = decimalFormat.format(cppTBB)
            preferences.put(StringNonKey.TotalBaseBasal, tbb)
        }
        binding.totalBaseBasal.setText(tbb)
        if (!activePlugin.activePump.pumpDescription.needsManualTDDLoad) binding.reload.visibility = View.GONE

        // stats table

        // add stats headers to tables
        binding.mainTable.addView(
            TableRow(this).also { trHead ->
                trHead.setBackgroundColor(rh.gac(this, app.aaps.core.ui.R.attr.tddHeaderBackground))
                trHead.layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
                trHead.addView(TextView(this).also { labelDate ->
                    labelDate.text = rh.gs(app.aaps.core.ui.R.string.date)
                    labelDate.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                })
                trHead.addView(TextView(this).also { labelBasalRate ->
                    labelBasalRate.text = rh.gs(app.aaps.core.ui.R.string.basalrate)
                    labelBasalRate.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                })
                trHead.addView(TextView(this).also { labelBolus ->
                    labelBolus.text = rh.gs(app.aaps.core.ui.R.string.bolus)
                    labelBolus.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                })
                trHead.addView(TextView(this).also { labelTdd ->
                    labelTdd.text = rh.gs(app.aaps.core.ui.R.string.tdd)
                    labelTdd.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                })
                trHead.addView(TextView(this).also { labelRatio ->
                    labelRatio.text = rh.gs(app.aaps.core.ui.R.string.ratio)
                    labelRatio.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                })
            }, TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
        )

        // cumulative table
        binding.cumulativeTable.addView(
            TableRow(this).also { ctrHead ->
                ctrHead.setBackgroundColor(rh.gac(this, app.aaps.core.ui.R.attr.tddHeaderBackground))
                ctrHead.layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
                ctrHead.addView(TextView(this).also { labelCumAmountDays ->
                    labelCumAmountDays.text = rh.gs(app.aaps.core.ui.R.string.amount_days)
                    labelCumAmountDays.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                })
                ctrHead.addView(TextView(this).also { labelCumTdd ->
                    labelCumTdd.text = rh.gs(app.aaps.core.ui.R.string.tdd)
                    labelCumTdd.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                })
                ctrHead.addView(TextView(this).also { labelCumRatio ->
                    labelCumRatio.text = rh.gs(app.aaps.core.ui.R.string.ratio)
                    labelCumRatio.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                })
            }, TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
        )

        // exponential table
        binding.expweightTable.addView(
            TableRow(this).also { etrHead ->
                etrHead.setBackgroundColor(rh.gac(this, app.aaps.core.ui.R.attr.tddHeaderBackground))
                etrHead.layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
                etrHead.addView(TextView(this).also { labelExpWeight ->
                    labelExpWeight.text = rh.gs(app.aaps.core.ui.R.string.weight)
                    labelExpWeight.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                })
                etrHead.addView(TextView(this).also { labelExpTdd ->
                    labelExpTdd.text = rh.gs(app.aaps.core.ui.R.string.tdd)
                    labelExpTdd.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                })
                etrHead.addView(TextView(this).also { labelExpRatio ->
                    labelExpRatio.text = rh.gs(app.aaps.core.ui.R.string.ratio)
                    labelExpRatio.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                })
            }, TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
        )

        binding.reload.setOnClickListener {
            binding.reload.visibility = View.GONE
            binding.connectionStatus.visibility = View.VISIBLE
            binding.message.visibility = View.VISIBLE
            binding.message.text = rh.gs(app.aaps.core.ui.R.string.warning_Message)
            commandQueue.loadTDDs(object : Callback() {
                override fun run() {
                    loadDataFromDB()
                    runOnUiThread {
                        binding.reload.visibility = View.VISIBLE
                        binding.connectionStatus.visibility = View.GONE
                        binding.message.visibility = View.GONE
                    }
                }
            })
        }
        binding.totalBaseBasal.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.totalBaseBasal.clearFocus()
                return@setOnEditorActionListener true
            }
            false
        }
        binding.totalBaseBasal.setOnFocusChangeListener { _: View?, hasFocus: Boolean ->
            if (hasFocus) {
                binding.totalBaseBasal.text.clear()
            } else {
                binding.totalBaseBasal.text.toString().let {
                    preferences.put(StringNonKey.TotalBaseBasal, it)
                    tbb = it
                }
                loadDataFromDB()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.totalBaseBasal.windowToken, 0)
            }
        }
        loadDataFromDB()
    }

    override fun onResume() {
        super.onResume()
        disposable.add(
            rxBus
                .toObservable(EventPumpStatusChanged::class.java)
                .observeOn(aapsSchedulers.main)
                .subscribe({ event -> binding.connectionStatus.text = event.getStatus(this@TDDStatsActivity) }, fabricPrivacy::logException)
        )
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.totalBaseBasal.onFocusChangeListener = null
        binding.totalBaseBasal.setOnEditorActionListener(null)
        binding.reload.setOnClickListener(null)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val myView = currentFocus
            if (myView is EditText) {
                val rect = Rect()
                myView.getGlobalVisibleRect(rect)
                if (!rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    myView.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private val TDD.total
        get() = if (totalAmount > 0) totalAmount else basalAmount + bolusAmount

    @SuppressLint("SetTextI18n")
    private fun loadDataFromDB() {
        historyList.clear()
        // timestamp DESC sorting!
        historyList.addAll(persistenceLayer.getLastTotalDailyDoses(10, true))

        //only use newest 10
        historyList = historyList.subList(0, min(10, historyList.size))

        // dummies reset
        dummies.clear()

        //fill single gaps
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        for (i in 0 until historyList.size - 1) {
            val elem1 = historyList[i]
            val elem2 = historyList[i + 1]
            if (df.format(Date(elem1.timestamp)) != df.format(Date(elem2.timestamp + 25 * 60 * 60 * 1000))) {
                val dummy = TDD(
                    timestamp = elem1.timestamp - T.hours(24).msecs(),
                    basalAmount = elem1.basalAmount / 2.0,
                    bolusAmount = elem1.bolusAmount / 2.0
                )
                dummies.add(dummy)
                elem1.basalAmount /= 2.0
                elem1.bolusAmount /= 2.0
            }
        }
        historyList.addAll(dummies)
        historyList.sortWith { lhs: TDD, rhs: TDD -> (rhs.timestamp - lhs.timestamp).toInt() }
        runOnUiThread {
            cleanTable(binding.mainTable)
            cleanTable(binding.cumulativeTable)
            cleanTable(binding.expweightTable)
            val df1: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
            if (TextUtils.isEmpty(tbb)) {
                binding.totalBaseBasal.error = "Please Enter Total Base Basal"
                return@runOnUiThread
            } else {
                magicNumber = SafeParse.stringToDouble(tbb)
            }
            magicNumber *= 2.0
            binding.totalBaseBasal2.text = decimalFormat.format(magicNumber)
            var i = 0
            var sum = 0.0
            var weighted03 = 0.0
            var weighted05 = 0.0
            var weighted07 = 0.0

            //TDD table
            for (record in historyList) {
                val tdd = record.total

                // Create the table row
                binding.mainTable.addView(
                    TableRow(this@TDDStatsActivity).also { tr ->
                        if (i % 2 != 0) tr.setBackgroundColor(rh.gac(this, app.aaps.core.ui.R.attr.tddHeaderBackground))
                        if (dummies.contains(record))
                            tr.setBackgroundColor(rh.gac(this, app.aaps.core.ui.R.attr.dummyBackground))

                        tr.id = 100 + i
                        tr.layoutParams = TableLayout.LayoutParams(
                            TableLayout.LayoutParams.MATCH_PARENT,
                            TableLayout.LayoutParams.WRAP_CONTENT
                        )

                        // Here create the TextView dynamically
                        tr.addView(TextView(this@TDDStatsActivity).also { labelDATE ->
                            labelDATE.id = 200 + i
                            labelDATE.text = df1.format(Date(record.timestamp))
                            labelDATE.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                        })
                        tr.addView(TextView(this@TDDStatsActivity).also { labelBASAL ->
                            labelBASAL.id = 300 + i
                            labelBASAL.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, record.basalAmount)
                            labelBASAL.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                        })
                        tr.addView(TextView(this@TDDStatsActivity).also { labelBOLUS ->
                            labelBOLUS.id = 400 + i
                            labelBOLUS.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, record.bolusAmount)
                            labelBOLUS.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                        })
                        tr.addView(TextView(this@TDDStatsActivity).also { labelTDD ->
                            labelTDD.id = 500 + i
                            labelTDD.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, tdd)
                            labelTDD.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                        })
                        tr.addView(TextView(this@TDDStatsActivity).also { labelRATIO ->
                            labelRATIO.id = 600 + i
                            labelRATIO.text = (100 * tdd / magicNumber).roundToInt().toString() + "%"
                            labelRATIO.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                        })
                    }, TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
                )
                i++
            }
            i = 0

            //cumulative TDDs
            for (record in historyList) {
                if (historyList.isNotEmpty() && df1.format(Date(record.timestamp)) == df1.format(Date()))
                //Today should not be included
                    continue
                i++
                sum += record.total

                // Create the cumulative table row
                binding.cumulativeTable.addView(
                    TableRow(this@TDDStatsActivity).also { ctr ->
                        if (i % 2 == 0) ctr.setBackgroundColor(rh.gac(this, app.aaps.core.ui.R.attr.tddHeaderBackground))
                        ctr.id = 700 + i
                        ctr.layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)

                        // Here create the TextView dynamically
                        ctr.addView(TextView(this@TDDStatsActivity).also { labelDAYS ->
                            labelDAYS.id = 800 + i
                            labelDAYS.text = i.toString()
                            labelDAYS.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                        })

                        ctr.addView(TextView(this@TDDStatsActivity).also { labelCUMTDD ->
                            labelCUMTDD.id = 900 + i
                            labelCUMTDD.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, sum / i)
                            labelCUMTDD.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                        })

                        ctr.addView(TextView(this@TDDStatsActivity).also { labelCUMRATIO ->
                            labelCUMRATIO.id = 1000 + i
                            labelCUMRATIO.text = (100 * sum / i / magicNumber).roundToInt().toString() + "%"
                            labelCUMRATIO.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                        })
                    }, TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
                )
            }
            if (isOldData(historyList) && activePlugin.activePump.pumpDescription.needsManualTDDLoad) {
                binding.message.visibility = View.VISIBLE
                binding.message.text = rh.gs(app.aaps.core.ui.R.string.olddata_Message)
            } else binding.mainTable.setBackgroundColor(rh.gac(this, app.aaps.core.ui.R.attr.mainTableBackground))
            if (historyList.isNotEmpty() && df1.format(Date(historyList[0].timestamp)) == df1.format(Date())) {
                //Today should not be included
                historyList.removeAt(0)
            }
            historyList.reverse()
            i = 0
            for (record in historyList) {
                val tdd = record.total
                if (i == 0) {
                    weighted03 = tdd
                    weighted05 = tdd
                    weighted07 = tdd
                } else {
                    weighted07 = weighted07 * 0.3 + tdd * 0.7
                    weighted05 = weighted05 * 0.5 + tdd * 0.5
                    weighted03 = weighted03 * 0.7 + tdd * 0.3
                }
                i++
            }

            // Create the exponential table row
            binding.expweightTable.addView(
                TableRow(this@TDDStatsActivity).also { etr ->
                    if (i % 2 != 0) etr.setBackgroundColor(rh.gac(this, app.aaps.core.ui.R.attr.tddHeaderBackground))
                    etr.id = 1100 + i
                    etr.layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)

                    // Here create the TextView dynamically
                    etr.addView(TextView(this@TDDStatsActivity).also { labelWEIGHT ->
                        labelWEIGHT.id = 1200 + i
                        labelWEIGHT.text = "0.3\n0.5\n0.7"
                        labelWEIGHT.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                    })
                    etr.addView(TextView(this@TDDStatsActivity).also { labelEXPTDD ->
                        labelEXPTDD.id = 1300 + i
                        labelEXPTDD.text = """
                ${rh.gs(app.aaps.core.ui.R.string.format_insulin_units, weighted03)}
                ${rh.gs(app.aaps.core.ui.R.string.format_insulin_units, weighted05)}
                ${rh.gs(app.aaps.core.ui.R.string.format_insulin_units, weighted07)}
                """.trimIndent()
                        labelEXPTDD.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                    })
                    etr.addView(TextView(this@TDDStatsActivity).also { labelEXPRATIO ->
                        labelEXPRATIO.id = 1400 + i
                        labelEXPRATIO.text = """
                ${(100 * weighted03 / magicNumber).roundToInt()}%
                ${(100 * weighted05 / magicNumber).roundToInt()}%
                ${(100 * weighted07 / magicNumber).roundToInt()}%
                """.trimIndent()
                        labelEXPRATIO.setTextColor(rh.gac(this, app.aaps.core.ui.R.attr.defaultTextColor))
                    })
                }, TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
            )
        }
    }

    private fun cleanTable(table: TableLayout) {
        val childCount = table.childCount
        // Remove all rows except the first one
        if (childCount > 1) table.removeViews(1, childCount - 1)
    }

    private fun isOldData(historyList: List<TDD>): Boolean {
        val type = activePlugin.activePump.pumpDescription.pumpType
        val startsYesterday =
            type == PumpType.DANA_R || type == PumpType.DANA_RS || type == PumpType.DANA_RV2 || type == PumpType.DANA_R_KOREAN || type == PumpType.ACCU_CHEK_INSIGHT_VIRTUAL || type == PumpType.DIACONN_G8
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        return historyList.size < 3 || df.format(Date(historyList[0].timestamp)) != df.format(Date(System.currentTimeMillis() - if (startsYesterday) 1000 * 60 * 60 * 24 else 0))
    }
}