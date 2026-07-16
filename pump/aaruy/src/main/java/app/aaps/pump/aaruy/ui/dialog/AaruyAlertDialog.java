package app.aaps.pump.aaruy.ui.dialog;


import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import app.aaps.pump.aaruy.R;

/**
 * 通用对话框类
 *
 * @use new AlertDialog(...).show();
 */
public class AaruyAlertDialog extends Dialog implements View.OnClickListener {

    /**
     * 自定义Dialog监听器
     */
    public interface OnDialogButtonClickListener {

        /**
         * 点击按钮事件的回调方法
         *
         * @param requestCode 传入的用于区分某种情况下的showDialog
         * @param isPositive
         */
        void onDialogButtonClick(int requestCode, boolean isPositive);
    }


    @SuppressWarnings("unused")
    private final Context context;
    private final String title;
    private String confirmText;
    private String cancelText;
    private String message;
    private boolean showNegativeButton = true;
    private final int requestCode;
    private final OnDialogButtonClickListener listener;

    /**
     * 带监听器参数的构造函数
     */
    public AaruyAlertDialog(Context context, String title, String message, boolean showNegativeButton,
                       int requestCode, OnDialogButtonClickListener listener) {
        super(context, R.style.MyDialog);

        this.context = context;
        this.title = title;
        this.message = message;
        this.showNegativeButton = showNegativeButton;
        this.requestCode = requestCode;
        this.listener = listener;
    }

    public AaruyAlertDialog(Context context, String title, String message, boolean showNegativeButton,
                       String strPositive, int requestCode, OnDialogButtonClickListener listener) {
        super(context, R.style.MyDialog);

        this.context = context;
        this.title = title;
        this.message = message;
        this.showNegativeButton = showNegativeButton;
        this.requestCode = requestCode;
        this.listener = listener;
    }

    public AaruyAlertDialog(Context context, String title, String message,
                       String strPositive, String strNegative, int requestCode, OnDialogButtonClickListener listener) {
        super(context, R.style.MyDialog);

        this.context = context;
        this.title = title;
        this.message = message;
        this.requestCode = requestCode;
        this.listener = listener;
        this.cancelText = strNegative;
        this.confirmText = strPositive;
    }

    private TextView tvMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.alert_dialog);

        Window window = getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.dimAmount = 0.8f;
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.CENTER;
            window.setAttributes(layoutParams);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

//            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.8);
//            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
//            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        setCanceledOnTouchOutside(false);

        TextView tvTitle = findViewById(R.id.tvAlertDialogTitle);
        tvMessage = findViewById(R.id.tvAlertDialogMessage);
        Button btnPositive = findViewById(R.id.btnAlertDialogPositive);
        Button btnNegative = findViewById(R.id.btnAlertDialogNegative);
        if (confirmText != null && !confirmText.isEmpty()) {
            btnPositive.setText(confirmText);
        }
        if (cancelText != null && !cancelText.isEmpty()) {
            btnNegative.setText(cancelText);
        }
        if (title != null && !title.isEmpty()) {
            tvTitle.setText(title);
        }
        btnPositive.setOnClickListener(this);
        if (showNegativeButton) {
            btnNegative.setOnClickListener(this);
        } else {
            btnNegative.setVisibility(View.GONE);
        }
        if (message != null && !message.isEmpty()) {
            tvMessage.setText(message);
        }

    }

    @Override
    public void onClick(final View v) {
        if (v.getId() == R.id.btnAlertDialogPositive) {
            listener.onDialogButtonClick(requestCode, true);
        } else if (v.getId() == R.id.btnAlertDialogNegative) {
            listener.onDialogButtonClick(requestCode, false);
        }

        dismiss();
    }

    public void setMessage(String message) {
        this.message = message;
        if (tvMessage != null) {
            tvMessage.setText(message.trim());
        }
    }
}