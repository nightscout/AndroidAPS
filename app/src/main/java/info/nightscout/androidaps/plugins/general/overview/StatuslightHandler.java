package info.nightscout.androidaps.plugins.general.overview;

import android.view.View;
import android.widget.TextView;

import androidx.arch.core.util.Function;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SetWarnColor;

class StatuslightHandler {

    /**
     * applies the statuslight subview on the overview fragement
     */
    void statuslight(TextView cageView, TextView iageView, TextView reservoirView,
                     TextView sageView, TextView batteryView) {
        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();

        applyStatuslight("cage", CareportalEvent.SITECHANGE, cageView, "CAN", 48, 72);
        applyStatuslight("iage", CareportalEvent.INSULINCHANGE, iageView, "INS", 72, 96);

        double reservoirLevel = pump.isInitialized() ? pump.getReservoirLevel() : -1;
        applyStatuslightLevel(R.string.key_statuslights_res_critical, 10.0,
                R.string.key_statuslights_res_warning, 80.0, reservoirView, "RES", reservoirLevel);

        applyStatuslight("sage", CareportalEvent.SENSORCHANGE, sageView, "SEN", 164, 166);

        if (pump.model() != PumpType.AccuChekCombo) {
            double batteryLevel = pump.isInitialized() ? pump.getBatteryLevel() : -1;
            applyStatuslightLevel(R.string.key_statuslights_bat_critical, 5.0,
                    R.string.key_statuslights_bat_warning, 22.0,
                    batteryView, "BAT", batteryLevel);
        } else {
            applyStatuslight("bage", CareportalEvent.PUMPBATTERYCHANGE, batteryView, "BAT", 224, 336);
        }

    }

    void applyStatuslight(String nsSettingPlugin, String eventName, TextView view, String text,
                          int defaultWarnThreshold, int defaultUrgentThreshold) {
        NSSettingsStatus nsSettings = NSSettingsStatus.getInstance();

        if (view != null) {
            double urgent = nsSettings.getExtendedWarnValue(nsSettingPlugin, "urgent", defaultUrgentThreshold);
            double warn = nsSettings.getExtendedWarnValue(nsSettingPlugin, "warn", defaultWarnThreshold);
            CareportalEvent event = MainApp.getDbHelper().getLastCareportalEvent(eventName);
            double age = event != null ? event.getHoursFromStart() : Double.MAX_VALUE;
            applyStatuslight(view, text, age, warn, urgent, Double.MAX_VALUE, true);
        }
    }

    void applyStatuslightLevel(int criticalSetting, double criticalDefaultValue,
                               int warnSetting, double warnDefaultValue,
                               TextView view, String text, double level) {
        if (view != null) {
            double resUrgent = SP.getDouble(criticalSetting, criticalDefaultValue);
            double resWarn = SP.getDouble(warnSetting, warnDefaultValue);
            applyStatuslight(view, text, level, resWarn, resUrgent, -1, false);
        }
    }

    void applyStatuslight(TextView view, String text, double value, double warnThreshold,
                                         double urgentThreshold, double invalid, boolean checkAscending) {
        Function<Double, Boolean> check = checkAscending ? (Double threshold) -> value >= threshold :
                (Double threshold) -> value <= threshold;
        if (value != invalid) {
            view.setText(text);
            if (check.apply(urgentThreshold)) {
                view.setTextColor(MainApp.gc(R.color.ribbonCritical));
            } else if (check.apply(warnThreshold)) {
                view.setTextColor(MainApp.gc(R.color.ribbonWarning));
            } else {
                view.setTextColor(MainApp.gc(R.color.ribbonDefault));
            }
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }

    }

    /**
     * applies the extended statuslight subview on the overview fragement
     */
    void extendedStatuslight(TextView cageView, TextView iageView,
                             TextView reservoirView, TextView sageView,
                             TextView batteryView) {
        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();

        handleAge("cage", CareportalEvent.SITECHANGE, cageView, "CAN ",
                48, 72);

        handleAge("iage", CareportalEvent.INSULINCHANGE, iageView, "INS ",
                72, 96);

        handleLevel(R.string.key_statuslights_res_critical, 10.0,
                R.string.key_statuslights_res_warning, 80.0,
                reservoirView, "RES ", pump.getReservoirLevel());

        handleAge("sage", CareportalEvent.SENSORCHANGE, sageView, "SEN ",
                164, 166);

        if (pump.model() != PumpType.AccuChekCombo) {
            handleLevel(R.string.key_statuslights_bat_critical, 26.0,
                    R.string.key_statuslights_bat_warning, 51.0,
                    batteryView, "BAT ", pump.getBatteryLevel());
        } else {
            handleAge("bage", CareportalEvent.PUMPBATTERYCHANGE, batteryView, "BAT ",
                    224, 336);
        }
    }

    void handleAge(String nsSettingPlugin, String eventName, TextView view, String text,
                   int defaultWarnThreshold, int defaultUrgentThreshold) {
        NSSettingsStatus nsSettings = new NSSettingsStatus().getInstance();

        if (view != null) {
            double urgent = nsSettings.getExtendedWarnValue(nsSettingPlugin, "urgent", defaultUrgentThreshold);
            double warn = nsSettings.getExtendedWarnValue(nsSettingPlugin, "warn", defaultWarnThreshold);
            CareportalFragment.handleAge(view, text, eventName, warn, urgent, true);
        }
    }

    void handleLevel(int criticalSetting, double criticalDefaultValue,
                     int warnSetting, double warnDefaultValue,
                     TextView view, String text, double level) {
        if (view != null) {
            double resUrgent = SP.getDouble(criticalSetting, criticalDefaultValue);
            double resWarn = SP.getDouble(warnSetting, warnDefaultValue);
            view.setText(text + DecimalFormatter.to0Decimal(level));
            SetWarnColor.setColorInverse(view, level, resWarn, resUrgent);
        }
    }

}
