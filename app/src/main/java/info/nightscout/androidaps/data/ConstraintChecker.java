package info.nightscout.androidaps.data;

import java.util.ArrayList;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.utils.SP;

/**
 * Created by mike on 19.03.2018.
 */

public class ConstraintChecker implements ConstraintsInterface {
    
    private MainApp mainApp;
    
    public ConstraintChecker(MainApp mainApp) {
        this.mainApp = mainApp;
    }


    public Constraint<Boolean> isLoopInvokationAllowed() {
        return isLoopInvokationAllowed(new Constraint<>(true));
    }

    public Constraint<Boolean> isClosedLoopAllowed() {
        return isClosedLoopAllowed(new Constraint<>(true));
    }

     public Constraint<Boolean> isAutosensModeEnabled() {
        return isAutosensModeEnabled(new Constraint<>(true));
    }

    @Override
    public Constraint<Boolean> isLoopInvokationAllowed(Constraint<Boolean> value) {

        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            constraint.isLoopInvokationAllowed(value);
        }
        return value;
    }

    @Override
    public Constraint<Boolean> isClosedLoopAllowed(Constraint<Boolean> value) {

        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            constraint.isClosedLoopAllowed(value);
        }
        return value;
    }

    @Override
    public Constraint<Boolean> isAutosensModeEnabled(Constraint<Boolean> value) {

        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            constraint.isAutosensModeEnabled(value);
        }
        return value;
    }

    @Override
    public boolean isAMAModeEnabled() {
        boolean result = SP.getBoolean("openapsama_useautosens", false);

        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            result = result && constrain.isAMAModeEnabled();
        }
        return result;
    }

    @Override
    public boolean isSMBModeEnabled() {
        boolean result = true; // TODO update for SMB // SP.getBoolean("openapsama_useautosens", false);

        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            result = result && constrain.isSMBModeEnabled();
        }
        return result;
    }

    @Override
    public Double applyBasalConstraints(Double absoluteRate) {
        Double rateAfterConstrain = absoluteRate;
        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            rateAfterConstrain = Math.min(constrain.applyBasalConstraints(absoluteRate), rateAfterConstrain);
        }
        return rateAfterConstrain;
    }

    @Override
    public Integer applyBasalConstraints(Integer percentRate) {
        Integer rateAfterConstrain = percentRate;
        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            rateAfterConstrain = Math.min(constrain.applyBasalConstraints(percentRate), rateAfterConstrain);
        }
        return rateAfterConstrain;
    }

    @Override
    public Double applyBolusConstraints(Double insulin) {
        Double insulinAfterConstrain = insulin;
        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            insulinAfterConstrain = Math.min(constrain.applyBolusConstraints(insulin), insulinAfterConstrain);
        }
        return insulinAfterConstrain;
    }

    @Override
    public Integer applyCarbsConstraints(Integer carbs) {
        Integer carbsAfterConstrain = carbs;
        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            carbsAfterConstrain = Math.min(constrain.applyCarbsConstraints(carbs), carbsAfterConstrain);
        }
        return carbsAfterConstrain;
    }

    @Override
    public Double applyMaxIOBConstraints(Double maxIob) {
        Double maxIobAfterConstrain = maxIob;
        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            maxIobAfterConstrain = Math.min(constrain.applyMaxIOBConstraints(maxIob), maxIobAfterConstrain);
        }
        return maxIobAfterConstrain;
    }


}
