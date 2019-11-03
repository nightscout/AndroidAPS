package info.nightscout.androidaps.plugins.sensitivity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.SensitivityInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SafeParse;

public abstract class AbstractSensitivityPlugin extends PluginBase implements SensitivityInterface {

    private static final Logger log = LoggerFactory.getLogger(L.AUTOSENS);

    public AbstractSensitivityPlugin(PluginDescription pluginDescription) {
        super(pluginDescription);
    }

    @Override
    public abstract AutosensResult detectSensitivity(IobCobCalculatorPlugin plugin, long fromTime, long toTime);

    AutosensResult fillResult(double ratio, double carbsAbsorbed, String pastSensitivity,
                              String ratioLimit, String sensResult, int deviationsArraySize) {
        return this.fillResult(ratio, carbsAbsorbed, pastSensitivity, ratioLimit, sensResult,
                deviationsArraySize,
                SafeParse.stringToDouble(SP.getString(R.string.key_openapsama_autosens_min, "0.7")),
                SafeParse.stringToDouble(SP.getString(R.string.key_openapsama_autosens_max, "1.2")));
    }

    public AutosensResult fillResult(double ratio, double carbsAbsorbed, String pastSensitivity,
                                     String ratioLimit, String sensResult, int deviationsArraySize,
                                     double ratioMin, double ratioMax) {
        double rawRatio = ratio;
        ratio = Math.max(ratio, ratioMin);
        ratio = Math.min(ratio, ratioMax);

        //If not-excluded data <= MIN_HOURS -> don't do Autosens
        //If not-excluded data >= MIN_HOURS_FULL_AUTOSENS -> full Autosens
        //Between MIN_HOURS and MIN_HOURS_FULL_AUTOSENS: gradually increase autosens
        double autosensContrib = (Math.min(Math.max(MIN_HOURS, deviationsArraySize / 12d),
                MIN_HOURS_FULL_AUTOSENS) - MIN_HOURS) / (MIN_HOURS_FULL_AUTOSENS - MIN_HOURS);
        ratio = autosensContrib * (ratio - 1) + 1;

        if (autosensContrib != 1d) {
            ratioLimit += "(" + deviationsArraySize + " of " + MIN_HOURS_FULL_AUTOSENS * 12 + " values) ";
        }

        if (ratio != rawRatio) {
            ratioLimit += "Ratio limited from " + rawRatio + " to " + ratio;
            if (L.isEnabled(L.AUTOSENS))
                log.debug(ratioLimit);
        }

        AutosensResult output = new AutosensResult();
        output.ratio = Round.roundTo(ratio, 0.01);
        output.carbsAbsorbed = Round.roundTo(carbsAbsorbed, 0.01);
        output.pastSensitivity = pastSensitivity;
        output.ratioLimit = ratioLimit;
        output.sensResult = sensResult;
        return output;
    }


}
