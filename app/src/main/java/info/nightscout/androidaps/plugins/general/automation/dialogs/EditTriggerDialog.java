package info.nightscout.androidaps.plugins.general.automation.dialogs;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger;

public class EditTriggerDialog extends DialogFragment {

    public interface OnClickListener {
        void onClick(Trigger newTriggerObject);
    }

    private static OnClickListener mClickListener = null;

    @BindView(R.id.layoutTrigger)
    LinearLayout mLayoutTrigger;

    private Trigger mTrigger;
    private Unbinder mUnbinder;

    public static EditTriggerDialog newInstance(Trigger trigger) {
        Bundle args = new Bundle();
        args.putString("trigger", trigger.toJSON());
        EditTriggerDialog fragment = new EditTriggerDialog();
        fragment.setArguments(args);
        return fragment;
    }

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.automation_dialog_edit_trigger, container, false);
        mUnbinder = ButterKnife.bind(this, view);

        // load data from bundle
        Bundle bundle = savedInstanceState != null ? savedInstanceState : getArguments();
        if (bundle != null) {
            String triggerData = bundle.getString("trigger");
            if (triggerData != null) mTrigger = Trigger.instantiate(triggerData);
        }

        // display root trigger
        mLayoutTrigger.addView(mTrigger.createView(getContext(), getFragmentManager()));

        return view;
    }

    public static void setOnClickListener(OnClickListener clickListener) {
        mClickListener = clickListener;
    }

    @Override
    public void onDestroyView() {
        mUnbinder.unbind();
        super.onDestroyView();
    }

    @OnClick(R.id.ok)
    public void onButtonOk(View view) {
        if (mClickListener != null)
            mClickListener.onClick(mTrigger);

        dismiss();
    }

    @OnClick(R.id.cancel)
    public void onButtonCancel(View view) {
        dismiss();
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        bundle.putString("trigger", mTrigger.toJSON());
    }
}
