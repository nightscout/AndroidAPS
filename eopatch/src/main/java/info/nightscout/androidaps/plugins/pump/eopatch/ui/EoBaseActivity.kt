package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.plugins.pump.eopatch.EoPatchRxBus
import info.nightscout.androidaps.plugins.pump.eopatch.dagger.EopatchPluginQualifier
import info.nightscout.androidaps.plugins.pump.eopatch.extension.fillExtras
import info.nightscout.androidaps.plugins.pump.eopatch.extension.observeOnMainThread
import info.nightscout.androidaps.plugins.pump.eopatch.extension.subscribeDefault
import info.nightscout.androidaps.plugins.pump.eopatch.vo.ActivityResultEvent
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import javax.inject.Inject
import io.reactivex.rxkotlin.addTo

abstract class EoBaseActivity<B : ViewDataBinding> : NoSplashAppCompatActivity(), EoBaseNavigator {
    @Inject
    @EopatchPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    protected lateinit var binding: B

    private val compositeDisposable = CompositeDisposable()

    @LayoutRes
    abstract fun getLayoutId(): Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme_NoActionBar)

        binding = DataBindingUtil.setContentView(this, getLayoutId())
        binding.lifecycleOwner = this

    }

    override fun onStart() {
        super.onStart()
        window.decorView.systemUiVisibility = if(AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_NO)
                                                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                                              else
                                                View.SYSTEM_UI_FLAG_VISIBLE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun toast(message: String) {
        Toast.makeText(this@EoBaseActivity, message, Toast.LENGTH_SHORT).show()
    }

    override fun toast(@StringRes message: Int) {
        Toast.makeText(this@EoBaseActivity, message, Toast.LENGTH_SHORT).show()
    }

    override fun back() {
        if(supportFragmentManager.backStackEntryCount == 0) {
            finish()
        } else {
            supportFragmentManager.popBackStack()
        }
    }

    override fun finish(finishAffinity: Boolean) {
        if(finishAffinity) {
            finishAffinity()
        } else {
            finish()
        }
    }

    override fun startActivityForResult(action: Context.() -> Intent, requestCode: Int, vararg params: Pair<String, Any?>) {
        val intent = action(this)
        if(params.isNotEmpty()) intent.fillExtras(params)
        startActivityForResult(intent, requestCode)
    }

    override fun checkCommunication(onSuccess: () -> Unit, onCancel: (() -> Unit)?, onDiscard: (() -> Unit)?, goHomeAfterDiscard: Boolean) {
        EoPatchRxBus.listen(ActivityResultEvent::class.java)
            .doOnSubscribe { startActivityForResult({ EopatchActivity.createIntentForCheckConnection(this, goHomeAfterDiscard) }, 10001) }
            .observeOnMainThread()
            .subscribeDefault {
                if (it.requestCode == 10001) {
                    when (it.resultCode) {
                        RESULT_OK -> onSuccess.invoke()
                        RESULT_CANCELED -> onCancel?.invoke()
                        EopatchActivity.RESULT_DISCARDED -> onDiscard?.invoke()
                    }
                }
            }.addTo()
    }

    fun Disposable.addTo() = addTo(compositeDisposable)
}