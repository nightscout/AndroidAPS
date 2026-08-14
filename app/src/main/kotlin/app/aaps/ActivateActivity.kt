package app.aaps

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.ui.toast.ToastUtils

/**
 * 全屏设备激活页(深色, 仿截图像素实测规范)。
 * 唯一验证入口: 首次激活 / 365天过期再验证 都走这里, MainActivity 不再弹窗, 杜绝重复验证。
 * 验证成功 → RESULT_OK 返回 MainActivity → 由其 start() 进入主界面。
 * showKeyOnly=true: 仅展示设备标识+授权状态(从"获取密钥"进入), 不输码、不重置。
 * 适配: ScrollView 包裹 + 全 dp 尺寸 + 弹性宽度, 各机型/密度/小屏均可完整显示。
 */
class ActivateActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SHOW_KEY_ONLY = "show_key_only"
        private const val PREFS_NAME = "AppLock"
        // 视觉规范(截图像素实测)
        private const val BG = "#121212"
        private const val CARD_BG = "#1E1E1E"
        private const val BORDER = "#3A3A3A"
        private const val TXT_TITLE = "#FFFFFF"
        private const val TXT_SUB = "#C8C8C8"
        private const val TXT_BODY = "#C2C2C2"
        private const val TXT_LABEL = "#9E9E9E"
        private const val TXT_HINT = "#BBBBBB"
        private const val TXT_NOTE = "#B7B7B7"
        private const val TXT_ERR = "#F87171"
        private const val LINK = "#8AB4F8"
        private const val ACCENT = "#4CAF50"
    }

    private var showKeyOnly = false
    private var deviceId: String = ""
    private lateinit var idText: TextView
    private lateinit var inputInvite: EditText
    private lateinit var inputPhone: EditText
    private lateinit var btnConfirm: Button
    private lateinit var statusLine: TextView

    private fun dp(px: Int): Int = (px * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showKeyOnly = intent.getBooleanExtra(EXTRA_SHOW_KEY_ONLY, false)
        deviceId = loadDeviceId()
        if (deviceId.isEmpty() && !showKeyOnly) {
            // 无密钥 → 生成新设备标识
            val secret = TotpUtils.generateSecret()
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString("totp_secret", secret).apply()
            deviceId = TotpUtils.deviceIdHex(secret)
        }
        setContentView(buildUi())

        // 返回键: showKeyOnly → 直接退出; 激活页 → 无法绕过(退出应用)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(RESULT_CANCELED)
                finish()
            }
        })
    }

    private fun loadDeviceId(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val secret = prefs.getString("totp_secret", null) ?: return ""
        return TotpUtils.deviceIdHex(secret)
    }

    /** 设备标识两行展示: 32位hex 拆 16+16 */
    private fun deviceIdTwoLines(id: String): Pair<String, String> {
        if (id.length <= 16) return id to ""
        return id.substring(0, 16) to id.substring(16)
    }

    private fun buildUi(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(BG))
            setPadding(dp(24), dp(20), dp(24), dp(28))
        }

        if (showKeyOnly) {
            buildKeyOnlyUi(content)
        } else {
            buildActivateUi(content)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(content)
        }
        return scroll
    }

    // ================= 激活页 (showKeyOnly=false) =================
    private fun buildActivateUi(root: LinearLayout) {
        // 顶部: AAPS 图标居中
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.aaps_logo)
            layoutParams = LinearLayout.LayoutParams(dp(82), dp(82)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        })
        // 标题居中
        root.addView(TextView(this).apply {
            text = "欢迎使用AAPS"
            textSize = 28f
            setTextColor(Color.parseColor(TXT_TITLE))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        })
        // 副标题居中
        root.addView(TextView(this).apply {
            text = "请输入邀请码完成设备激活"
            textSize = 14f
            setTextColor(Color.parseColor(TXT_SUB))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(26))
        })

        // ── 手机号卡片 ──
        root.addView(card().apply {
            addView(label("📱 手机号(选填)"))
            inputPhone = EditText(this@ActivateActivity).apply {
                hint = "用于管理员登记"
                setHintTextColor(Color.parseColor(TXT_HINT))
                setTextColor(Color.parseColor(TXT_TITLE))
                textSize = 15f
                inputType = InputType.TYPE_CLASS_PHONE
                background = rounded(dp(10), "#161616", BORDER, 1)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                maxLines = 1
            }
            addView(inputPhone, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
        }, cardParams())

        // ── 设备标识卡片 ──
        root.addView(card().apply {
            val labelRow = LinearLayout(this@ActivateActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            labelRow.addView(label("🆔 设备标识 · 丹纳RS泵"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            labelRow.addView(Button(this@ActivateActivity).apply {
                text = "📋 复制"
                textSize = 12f
                setTextColor(Color.parseColor(LINK))
                background = rounded(dp(8), "#2A2A2A", "#3D4A5C", 1)
                isAllCaps = false
                setPadding(dp(12), dp(2), dp(12), dp(2))
                minHeight = 0
                setOnClickListener {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("deviceId", deviceId))
                    ToastUtils.okToast(this@ActivateActivity, "设备标识已复制")
                }
            })
            addView(labelRow)

            // 泵图标(小) + 设备标识两行
            val idRow = LinearLayout(this@ActivateActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(4))
            }
            idRow.addView(ImageView(this@ActivateActivity).apply {
                setImageResource(R.drawable.ic_dana_rs)
                // 泵图标横放, 旋转 -90° 竖放(导管朝上)
                rotation = -90f
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(48))
            }, LinearLayout.LayoutParams(dp(30), dp(48)).apply { rightMargin = dp(14) })

            val idLines = LinearLayout(this@ActivateActivity).apply {
                orientation = LinearLayout.VERTICAL
            }
            idText = TextView(this@ActivateActivity).apply {
                textSize = 16f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor(TXT_TITLE))
            }
            val (l1, l2) = deviceIdTwoLines(deviceId)
            idText.text = l1
            idLines.addView(idText)
            if (l2.isNotEmpty()) {
                idLines.addView(TextView(this@ActivateActivity).apply {
                    text = l2
                    textSize = 16f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#B9BEC4"))
                })
            }
            idRow.addView(idLines, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(idRow)

            // 提示
            addView(TextView(this@ActivateActivity).apply {
                text = "请复制上方设备ID发送给管理员获取邀请码"
                textSize = 12f
                setTextColor(Color.parseColor(TXT_BODY))
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            })

            // 复制带手机号(单独按钮, 不覆盖上方单复制设备标识)
            addView(LinearLayout(this@ActivateActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
                addView(Button(this@ActivateActivity).apply {
                    text = "📋 复制标识+手机号"
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    isAllCaps = false
                    background = rounded(dp(10), ACCENT, ACCENT, 0)
                    setPadding(dp(16), dp(6), dp(16), dp(6))
                    minHeight = 0
                    setOnClickListener {
                        val phone = inputPhone.text.toString().trim().takeIf { it.isNotBlank() }
                        val text = if (phone != null) "设备标识: $deviceId\n手机号: $phone" else "设备标识: $deviceId"
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("deviceInfo", text))
                        ToastUtils.okToast(this@ActivateActivity, if (phone != null) "设备标识+手机号已复制" else "设备标识已复制(未填手机号)")
                    }
                })
            })
        }, cardParams())

        // ── 邀请码卡片 ──
        root.addView(card().apply {
            addView(label("🔑 邀请码"))
            inputInvite = EditText(this@ActivateActivity).apply {
                hint = "请输入8位邀请码"
                setHintTextColor(Color.parseColor(TXT_HINT))
                setTextColor(Color.parseColor(TXT_TITLE))
                textSize = 15f
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                background = rounded(dp(10), "#161616", BORDER, 1)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                maxLines = 1
            }
            addView(inputInvite, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)))

            // 提示行: 区分大小写 + 获取密钥
            val noteRow = LinearLayout(this@ActivateActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
            }
            noteRow.addView(TextView(this@ActivateActivity).apply {
                text = "邀请码区分大小写 · 30秒有效"
                textSize = 11f
                setTextColor(Color.parseColor(TXT_NOTE))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            noteRow.addView(TextView(this@ActivateActivity).apply {
                text = "获取密钥"
                textSize = 12f
                setTextColor(Color.parseColor(LINK))
                setOnClickListener { showKeyOnlyDialog() }
            })
            addView(noteRow)
        }, cardParams())

        // 状态行(错误提示用)
        statusLine = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor(TXT_ERR))
            setPadding(0, dp(8), 0, 0)
            visibility = View.GONE
        }
        root.addView(statusLine)

        // ── 确认按钮 ──
        btnConfirm = Button(this).apply {
            text = "确认激活"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = rounded(dp(14), ACCENT, ACCENT, 0)
            setOnClickListener { onConfirm() }
        }
        root.addView(btnConfirm, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply {
            topMargin = dp(14)
        })

        // 底部链接
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
            addView(link("重置设备标识") { onResetDevice() })
            addView(TextView(this@ActivateActivity).apply {
                text = "  ·  "
                textSize = 13f
                setTextColor(Color.parseColor(TXT_NOTE))
            })
            addView(link("退出") {
                setResult(RESULT_CANCELED)
                finish()
            })
        })
    }

    // ================= 只读展示页 (showKeyOnly=true) =================
    private fun buildKeyOnlyUi(root: LinearLayout) {
        root.addView(TextView(this).apply {
            text = "🔑 获取密钥"
            textSize = 22f
            setTextColor(Color.parseColor(TXT_TITLE))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(6))
        })
        root.addView(card().apply {
            addView(label("🆔 设备标识 · 丹纳RS泵"))
            val (l1, l2) = deviceIdTwoLines(deviceId)
            val idLines = LinearLayout(this@ActivateActivity).apply { orientation = LinearLayout.VERTICAL }
            idLines.addView(TextView(this@ActivateActivity).apply {
                text = l1
                textSize = 18f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor(TXT_TITLE))
            })
            if (l2.isNotEmpty()) {
                idLines.addView(TextView(this@ActivateActivity).apply {
                    text = l2
                    textSize = 18f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#B9BEC4"))
                })
            }
            addView(idLines)
            addView(TextView(this@ActivateActivity).apply {
                text = "请将上方设备ID发给管理员获取邀请码"
                textSize = 12f
                setTextColor(Color.parseColor(TXT_BODY))
                setPadding(0, dp(10), 0, 0)
            })
        }, cardParams())

        statusLine = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor(TXT_NOTE))
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(statusLine)
        statusLine.text = authStatusText()

        btnConfirm = Button(this).apply {
            text = "知道了"
            textSize = 15f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = rounded(dp(12), ACCENT, ACCENT, 0)
            setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
        root.addView(btnConfirm, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
    }

    // ================= 获取密钥: 弹窗展示(不重置) =================
    private fun showKeyOnlyDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val (l1, l2) = deviceIdTwoLines(deviceId)
        val msg = buildString {
            append(l1)
            if (l2.isNotEmpty()) append("\n").append(l2)
            append("\n\n").append(authStatusText())
            append("\n\n请将设备ID发给管理员获取邀请码")
        }
        AlertDialog.Builder(this)
            .setTitle("设备标识")
            .setMessage(msg)
            .setPositiveButton("复制", { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("deviceId", deviceId))
                ToastUtils.okToast(this, "设备标识已复制")
            })
            .setNegativeButton("知道了", null)
            .show()
    }

    private fun authStatusText(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val verified = prefs.getBoolean("password_verified", false)
        val lastTs = prefs.getLong("last_verify_time", 0L)
        if (!verified || lastTs == 0L) return "授权状态: 尚未验证"
        val remainMs = TotpUtils.EXPIRE_MS - (System.currentTimeMillis() - lastTs)
        val days = remainMs / (24 * 60 * 60 * 1000)
        return if (remainMs > 0) "授权状态: 已验证, 剩余 $days 天"
        else "授权状态: 已过期 ${-days} 天, 请重新验证"
    }

    private fun onResetDevice() {
        AlertDialog.Builder(this)
            .setTitle("重置设备标识")
            .setMessage("重置后需重新获取邀请码激活, 确定继续?")
            .setPositiveButton("重置", { _, _ ->
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove("totp_secret")
                    .remove("password_verified")
                    .remove("last_verify_time")
                    .apply()
                ToastUtils.okToast(this, "已重置, 请重新激活")
                setResult(RESULT_CANCELED)
                finish()
            })
            .setNegativeButton("取消", null)
            .show()
    }

    private fun onConfirm() {
        if (showKeyOnly) { setResult(RESULT_CANCELED); finish(); return }
        try {
            val code = inputInvite.text.toString().trim()
            if (code.isEmpty()) { ToastUtils.errorToast(this, "请输入8位邀请码"); return }
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val secret = prefs.getString("totp_secret", null)
            if (secret == null) { ToastUtils.errorToast(this, "设备标识异常, 请重置"); return }
            if (TotpUtils.verifyTotp(secret, code)) {
                val phone = inputPhone.text.toString().trim()
                val editor = prefs.edit()
                if (phone.isNotEmpty()) editor.putString("phone", phone)
                editor.putBoolean("password_verified", true)
                editor.putLong("last_verify_time", System.currentTimeMillis())
                editor.apply()
                ToastUtils.okToast(this, "验证成功!365天后需重新验证")
                setResult(RESULT_OK)
                finish()
            } else {
                // 邀请码错误: 醒目弹窗提示 + 清空输入框
                ToastUtils.errorToast(this, "邀请码错误, 请核对后重试")
                try {
                    AlertDialog.Builder(this)
                        .setTitle("❌ 邀请码错误")
                        .setMessage("输入的邀请码不正确或已过期(30秒有效)。\n\n请确认:\n· 邀请码区分大小写\n· 30秒内输入完成\n· 与设备标识对应\n\n可点击\"获取密钥\"重新核对设备标识, 联系管理员重新生成邀请码。")
                        .setPositiveButton("重新输入", { _, _ ->
                            inputInvite.text.clear()
                            inputInvite.requestFocus()
                        })
                        .setNegativeButton("复制设备标识", { _, _ ->
                            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("deviceId", deviceId))
                            ToastUtils.okToast(this, "设备标识已复制")
                        })
                        .show()
                } catch (e2: Exception) {
                    // 弹窗失败不致命, 已用 Toast 提示
                }
            }
        } catch (e: Exception) {
            // 异常直接显示, 便于定位(正常不会走到这里)
            android.util.Log.e("ActivateActivity", "onConfirm 异常: " + e.message, e)
            ToastUtils.errorToast(this, "验证异常: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // ================= UI 构建辅助 =================
    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12.5f
        setTextColor(Color.parseColor(TXT_LABEL))
        setPadding(0, 0, 0, dp(8))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(dp(14), CARD_BG, BORDER, 1)
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    private fun cardParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        }

    private fun link(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor(LINK))
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setOnClickListener { onClick() }
    }

    private fun rounded(radius: Int, fill: String, stroke: String, strokeW: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(Color.parseColor(fill))
            if (strokeW > 0) setStroke(strokeW, Color.parseColor(stroke))
        }
}
