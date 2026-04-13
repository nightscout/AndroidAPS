package app.aaps

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.text.InputType
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.util.Linkify
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import app.aaps.activities.HistoryBrowseActivity
import app.aaps.activities.PreferencesActivity
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventRebuildTabs
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.UIRunnable
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.isRunningRealPumpTest
import app.aaps.databinding.ActivityMainBinding
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import app.aaps.plugins.configuration.setupwizard.SetupWizardActivity
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.ui.activities.ProfileHelperActivity
import app.aaps.ui.activities.StatsActivity
import app.aaps.ui.activities.TreatmentsActivity
import app.aaps.ui.tabs.TabPageAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.joanzapata.iconify.Iconify
import com.joanzapata.iconify.fonts.FontAwesomeModule
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

class MainActivity : DaggerAppCompatActivityWithResult() {

    // 弹窗队列管理（彻底修复变量引用问题）
    private val dialogQueue = mutableListOf<() -> Unit>()
    private var isDialogShowing = false

    // 自定义Base32编解码
    private object Base32Coder {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" // 修复拼写警告

        fun encode(input: ByteArray): String {
            val output = StringBuilder()
            var buffer = 0
            var bitsLeft = 0
            for (b in input) {
                buffer = (buffer shl 8) or (b.toInt() and 0xFF)
                bitsLeft += 8
                while (bitsLeft >= 5) {
                    bitsLeft -= 5
                    output.append(ALPHABET[(buffer shr bitsLeft) and 0x1F])
                }
            }
            if (bitsLeft > 0) {
                buffer = buffer shl (5 - bitsLeft)
                output.append(ALPHABET[buffer and 0x1F])
            }
            return output.toString()
        }

        fun decode(input: String): ByteArray {
            val cleanInput = input.uppercase().trimEnd('=')
            val output = ByteArray(cleanInput.length * 5 / 8)
            var buffer = 0
            var bitsLeft = 0
            var index = 0
            for (c in cleanInput) {
                val value = ALPHABET.indexOf(c)
                if (value < 0) throw IllegalArgumentException("无效字符: $c")
                buffer = (buffer shl 5) or value
                bitsLeft += 5
                if (bitsLeft >= 8) {
                    output[index++] = (buffer shr (bitsLeft - 8)).toByte()
                    bitsLeft -= 8
                }
            }
            return output.copyOf(index)
        }
    }

    // TOTP核心实现
    private fun verifyTotp(secret: ByteArray, code: String, tolerance: Int = 1): Boolean {
        if (code.length != 6) return false
        val codeNum = code.toIntOrNull() ?: return false

        val timeStepMs = 30L * 1000L
        val currentTime = System.currentTimeMillis()

        for (offset in -tolerance..tolerance) {
            val checkTime = currentTime + offset * timeStepMs
            val counter = checkTime / timeStepMs
            val generatedCode = generateTotp(secret, counter)
            if (generatedCode == codeNum) {
                return true
            }
        }
        return false
    }

    private fun generateTotp(secret: ByteArray, counter: Long): Int {
        val counterBytes = ByteArray(8)
        var temp = counter
        for (i in 7 downTo 0) {
            counterBytes[i] = (temp and 0xFF).toByte()
            temp = temp shr 8
        }

        val mac = Mac.getInstance("HmacSHA1")
        val secretKey = SecretKeySpec(secret, "HmacSHA1")
        mac.init(secretKey)
        val hmacResult = mac.doFinal(counterBytes)

        val offset = hmacResult.last().toInt() and 0x1F
        val binary = ((hmacResult[offset].toInt() and 0x7F) shl 24) or
            ((hmacResult[offset + 1].toInt() and 0xFF) shl 16) or
            ((hmacResult[offset + 2].toInt() and 0xFF) shl 8) or
            (hmacResult[offset + 3].toInt() and 0xFF)

        return binary % 1000000
    }

    private val disposable = CompositeDisposable()

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var versionCheckerUtils: VersionCheckerUtils
    @Inject lateinit var smsCommunicator: SmsCommunicator
    @Inject lateinit var loop: Loop
    @Inject lateinit var config: Config
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var iconsProvider: IconsProvider
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var signatureVerifierPlugin: SignatureVerifierPlugin
    @Inject lateinit var maintenancePlugin: MaintenancePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var fileListProvider: FileListProvider
    @Inject lateinit var cryptoUtil: CryptoUtil
    @Inject lateinit var exportPasswordDataStore: ExportPasswordDataStore
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var configBuilder: ConfigBuilder

    private var actionBarDrawerToggle: ActionBarDrawerToggle? = null
    private var pluginPreferencesMenuItem: MenuItem? = null
    private var menu: Menu? = null
    private var menuOpen = false
    private var isProtectionCheckActive = false
    private lateinit var binding: ActivityMainBinding
    private var mainMenuProvider: MenuProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Iconify.with(FontAwesomeModule())
        LocaleHelper.update(applicationContext)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        initAllComponents(savedInstanceState)

        // 延迟300ms，确保布局渲染完成
        Handler(Looper.getMainLooper()).postDelayed({
                                                        showSetupOrVerification()
                                                    }, 300)
    }

    // 核心修复：使用队列索引管理，避免依赖外部dialog变量
    private fun enqueueDialog(dialogTask: () -> Unit) {
        dialogQueue.add(dialogTask)
        processDialogQueue()
    }

    private fun processDialogQueue() {
        if (isDialogShowing || dialogQueue.isEmpty()) return
        isDialogShowing = true
        // 修复：使用标准索引0取出，兼容所有API版本
        val task = dialogQueue.removeAt(0)
        task.invoke()
    }

    private fun onDialogDismissed() {
        isDialogShowing = false
        processDialogQueue()
    }

    private fun showSetupOrVerification() {
        val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
        val verified = prefs.getBoolean("password_verified", false)
        val hasTotpSecret = prefs.getString("totp_secret", null) != null

        if (!verified) {
            if (!hasTotpSecret) {
                enqueueDialog { showSetupGuideDialog() }
            } else {
                enqueueDialog { showPasswordVerificationDialog() }
            }
        }
    }

    // 修复：dialog -> dialogView，直接操作布局
    private fun showSetupGuideDialog() {
        val secret = ByteArray(20)
        SecureRandom().nextBytes(secret)
        val secretBase32 = Base32Coder.encode(secret)

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16))

            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.setup_totp_guide, secretBase32) // 修复：使用字符串资源
                textSize = 14f
                setLineSpacing(1.2f, 1f)
            })

            val keyText = TextView(this@MainActivity).apply {
                text = getString(R.string.totp_secret_key, secretBase32) // 修复：使用资源
                textSize = 18f
                setTextColor(Color.BLUE)
                setPadding(0, dp2px(16), 0, dp2px(16))
            }
            addView(keyText)

            val input = EditText(this@MainActivity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = getString(R.string.enter_totp_code) // 修复：使用资源
                maxLines = 1
                id = View.generateViewId()
            }
            addView(input)

            // 显示对话框
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.setup_totp_title)
                .setView(this)
                .setCancelable(false)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    val code = input.text.toString()
                    val secretBytes = Base32Coder.decode(secretBase32)
                    if (verifyTotp(secretBytes, code)) {
                        prefs.edit()
                            .putString("totp_secret", secretBase32)
                            .putBoolean("password_verified", true)
                            .apply()
                        ToastUtils.okToast(this@MainActivity, R.string.setup_success)
                        onDialogDismissed()
                    } else {
                        ToastUtils.errorToast(this@MainActivity, R.string.invalid_code)
                        showSetupGuideDialog()
                    }
                }
                .setNegativeButton(R.string.exit) { _, _ ->
                    finish()
                }
                .setOnDismissListener {
                    onDialogDismissed()
                }
                .show()
        }
    }

    // 修复：dialog -> 直接构建对话框，移除未使用变量
    private fun showPasswordVerificationDialog() {
        val maskView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#CC000000"))
            isClickable = true
        }
        val rootView = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        rootView.addView(maskView)

        val passwordInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.enter_totp_code)
            setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16))
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.verify_totp_title)
            .setView(passwordInput)
            .setCancelable(false)
            .setPositiveButton(R.string.verify) { _, _ ->
                val code = passwordInput.text.toString()
                val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
                val secretBase32 = prefs.getString("totp_secret", null) ?: return@setPositiveButton
                val secretBytes = Base32Coder.decode(secretBase32)

                if (verifyTotp(secretBytes, code)) {
                    prefs.edit().putBoolean("password_verified", true).apply()
                    rootView.removeView(maskView)
                    dialog.dismiss()
                    onDialogDismissed()
                    ToastUtils.okToast(this@MainActivity, R.string.verify_success)
                } else {
                    ToastUtils.errorToast(this@MainActivity, R.string.invalid_code)
                    dialog.dismiss()
                    showPasswordVerificationDialog()
                }
            }
            .setNegativeButton(R.string.exit) { _, _ ->
                rootView.removeView(maskView)
                finish()
            }
            .setOnDismissListener {
                rootView.removeView(maskView)
                onDialogDismissed()
            }
            .show()
    }

    // 修复：使用KTX风格的dp转px
    @Suppress("UseValueOf")
    private fun dp2px(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

    // 以下为原有生命周期方法，未改动，修复语法提示
    private fun initAllComponents(savedInstanceState: Bundle?) {
        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.mainDrawerLayout, R.string.open_navigation, R.string.close_navigation).also {
            binding.mainDrawerLayout.addDrawerListener(it)
            it.syncState()
        }

        processPreferenceChange(EventPreferenceChange(BooleanKey.OverviewKeepScreenOn.key))

        disposable += rxBus
            .toObservable(EventRebuildTabs::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           if (it.recreate) recreate()
                           else setupViews()
                           setWakeLock()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ processPreferenceChange(it) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAppInitialized::