package app.aaps.pump.aaruy.ui.dialog;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.lang.ref.WeakReference;

/**
 * Created by zmt on 2024/06/24
 */
public class CustomDialogFragment extends DialogFragment {
    private int layoutResId; // 布局资源ID

    private View view;

    public interface DialogFragmentInterface {
        public void getView(View view);
    }

    private DialogFragmentInterface myViewInterface;

    public void setMyViewInterface(DialogFragmentInterface myViewInterface) {
        this.myViewInterface = myViewInterface;
    }

    public View getView() {
        return view;
    }


    // 静态方法来创建实例
    public static CustomDialogFragment newInstance(int layoutResId) {
        CustomDialogFragment fragment = new CustomDialogFragment();
        Bundle args = new Bundle();
        args.putInt("layoutResId", layoutResId);
        fragment.setArguments(args);
        WeakReference<CustomDialogFragment> weakFragment = new WeakReference<>(fragment);
        return weakFragment.get();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            layoutResId = getArguments().getInt("layoutResId");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // 在对话框显示后设置宽度
        Window window = getDialog().getWindow();
        if (window != null) {
            // 设置宽度为屏幕宽度的 80%
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.8);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // 可选：使背景透明
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        view = inflater.inflate(layoutResId, container, false);
        if (myViewInterface != null) {
            myViewInterface.getView(view);
        }
        return view;
    }

    public void showDialog(FragmentManager manager) {
        this.show(manager, "CustomDialogFragment");
    }

}
