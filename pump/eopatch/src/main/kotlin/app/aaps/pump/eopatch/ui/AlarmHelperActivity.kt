package app.aaps.pump.eopatch.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.ui.R
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.pump.eopatch.alarm.AlarmCode
import app.aaps.pump.eopatch.event.EventDialog
import app.aaps.pump.eopatch.event.EventProgressDialog
import app.aaps.pump.eopatch.ui.dialogs.AlarmDialog
import app.aaps.pump.eopatch.ui.dialogs.ProgressDialogHelper
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class AlarmHelperActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private var disposable: CompositeDisposable = CompositeDisposable()
    private var mProgressDialog: AlertDialog? = null

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme_NoActionBar)

        val alarmDialog = AlarmDialog()
        alarmDialog.helperActivity = this
        intent.getStringExtra("code")?.let {
            alarmDialog.code = it
            alarmDialog.alarmCode = AlarmCode.fromStringToCode(it)
        }

        alarmDialog.status = intent.getStringExtra("status") ?: ""
        alarmDialog.sound = intent.getIntExtra("soundid", R.raw.error)
        alarmDialog.title = intent.getStringExtra("title") ?: ""
        if (alarmDialog.code != null)
            alarmDialog.show(supportFragmentManager, "Alarm")

        disposable.add(
            rxBus
                .toObservable(EventProgressDialog::class.java)
                .observeOn(aapsSchedulers.main)
                .subscribe({ if (it.show) showProgressDialog(it.resId) else dismissProgressDialog() }, { })
        )

        disposable.add(
            rxBus
                .toObservable(EventDialog::class.java)
                .observeOn(aapsSchedulers.main)
                .subscribe({ if (it.show) it.dialog.show(supportFragmentManager, "") }, { })
        )
    }

    private fun showProgressDialog(resId: Int) {
        if (mProgressDialog == null && resId != 0) {
            mProgressDialog = ProgressDialogHelper.get(this, getString(resId)).apply {
                setCancelable(false)
            }
            mProgressDialog?.show()
        }
    }

    private fun dismissProgressDialog() {
        mProgressDialog?.dismiss()
        mProgressDialog = null
    }
}