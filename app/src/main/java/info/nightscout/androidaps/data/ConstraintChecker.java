package info.nightscout.androidaps.data;

import java.util.ArrayList;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;

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

     public Constraint<Boolean> isAMAModeEnabled() {
        return isAMAModeEnabled(new Constraint<>(true));
    }

     public Constraint<Boolean> isSMBModeEnabled() {
        return isSMBModeEnabled(new Constraint<>(true));
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
    public Constraint<Boolean> isAMAModeEnabled(Constraint<Boolean> value) {

        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            constrain.isAMAModeEnabled(value);
        }
        return value;
    }

    @Override
    public Constraint<Boolean> isSMBModeEnabled(Constraint<Boolean> value) {

        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            constraint.isSMBModeEnabled(value);
        }
        return value;
    }

    @Override
    public Constraint<Double> applyBasalConstraints(Constraint<Double> absoluteRate, Profile profile) {
        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            constraint.applyBasalConstraints(absoluteRate, profile);
        }
        return absoluteRate;
    }

    @Override
    public Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, Profile profile) {
        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            constrain.applyBasalPercentConstraints(percentRate, profile);
        }
        return percentRate;
    }

    @Override
    public Constraint<Double> applyBolusConstraints(Constraint<Double> insulin) {
        ArrayList<PluginBase> constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginBase.CONSTRAINTS)) continue;
            constrain.applyBolusConstraints(insulin);
        }
        return insulin;
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
