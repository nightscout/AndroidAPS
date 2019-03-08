package info.nightscout.androidaps.plugins.general.maintenance.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;

public class LogSettingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logsetting);
        ButterKnife.bind(this);

        createViewsForSettings(L.getLogElements());
    }

    private void createViewsForSettings(List<L.LogElement> elements) {
        if (elements.size() == 0) return;
        LinearLayout container = (LinearLayout) findViewById(R.id.logsettings_placeholder);
        container.removeAllViews();
        for (L.LogElement element : elements) {
            PluginViewHolder pluginViewHolder = new PluginViewHolder(element);
            container.addView(pluginViewHolder.getBaseView());
        }
    }

    @OnClick(R.id.logsettings_reset)
    public void onResetClick() {
        L.resetToDefaults();
        createViewsForSettings(L.getLogElements());
    }

    public class PluginViewHolder {

        private Unbinder unbinder;
        private L.LogElement element;

        LinearLayout baseView;
        @BindView(R.id.logsettings_description)
        TextView description;
        @BindView(R.id.logsettings_visibility)
        CheckBox enabled;

        public PluginViewHolder(L.LogElement element) {
            this.element = element;
            baseView = (LinearLayout) getLayoutInflater().inflate(R.layout.logsettings_item, null);
            unbinder = ButterKnife.bind(this, baseView);

            description.setText(element.name);
            enabled.setChecked(element.enabled);
        }

        public View getBaseView() {
            return baseView;
        }

        @OnClick(R.id.logsettings_visibility)
        void onEnagledChanged() {
            element.setEnabled(enabled.isChecked());
        }

        public void unbind() {
            unbinder.unbind();
        }
    }

}
