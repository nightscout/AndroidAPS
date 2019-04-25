package info.nightscout.androidaps.data;

import android.support.annotation.NonNull;

import java.util.ArrayList;

import javax.annotation.Nonnull;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;

/**
 * Created by mike on 19.03.2018.
 */

public class ConstraintChecker implements ConstraintsInterface {

    public Constraint<Boolean> isLoopInvokationAllowed() {
        return isLoopInvocationAllowed(new Constraint<>(true));
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

    public Constraint<Boolean> isUAMEnabled() {
        return isUAMEnabled(new Constraint<>(true));
    }

    public Constraint<Boolean> isAdvancedFilteringEnabled() {
        return isAdvancedFilteringEnabled(new Constraint<>(true));
    }

    public Constraint<Boolean> isSuperBolusEnabled() {
        return isSuperBolusEnabled(new Constraint<>(true));
    }

    public Constraint<Double> getMaxBasalAllowed(Profile profile) {
        return applyBasalConstraints(new Constraint<>(Constants.REALLYHIGHBASALRATE), profile);
    }

    public Constraint<Integer> getMaxBasalPercentAllowed(Profile profile) {
        return applyBasalPercentConstraints(new Constraint<>(Constants.REALLYHIGHPERCENTBASALRATE), profile);
    }

    public Constraint<Double> getMaxBolusAllowed() {
        return applyBolusConstraints(new Constraint<>(Constants.REALLYHIGHBOLUS));
    }

    public Constraint<Double> getMaxExtendedBolusAllowed() {
        return applyExtendedBolusConstraints(new Constraint<>(Constants.REALLYHIGHBOLUS));
    }

    public Constraint<Integer> getMaxCarbsAllowed() {
        return applyCarbsConstraints(new Constraint<>(Constants.REALLYHIGHCARBS));
    }

    public Constraint<Double> getMaxIOBAllowed() {
        return applyMaxIOBConstraints(new Constraint<>(Constants.REALLYHIGHIOB));
    }

    @Override
    public Constraint<Boolean> isLoopInvocationAllowed(@NonNull Constraint<Boolean> value) {

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue;
            constraint.isLoopInvocationAllowed(value);
        }
        return value;
    }

    @Override
    public Constraint<Boolean> isClosedLoopAllowed(@NonNull Constraint<Boolean> value) {

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue;
            constraint.isClosedLoopAllowed(value);
        }
        return value;
    }

    @Override
    public Constraint<Boolean> isAutosensModeEnabled(@NonNull Constraint<Boolean> value) {

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue;
            constraint.isAutosensModeEnabled(value);
        }
        return value;
    }

    @Override
    public Constraint<Boolean> isAMAModeEnabled(@NonNull Constraint<Boolean> value) {

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue;
            constrain.isAMAModeEnabled(value);
        }
        return value;
    }

    @Override
    public Constraint<Boolean> isSMBModeEnabled(@NonNull Constraint<Boolean> value) {

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue;
            constraint.isSMBModeEnabled(value);
        }
        return value;
    }

    @Override
    public Constraint<Boolean> isUAMEnabled(@NonNull Constraint<Boolean> value) {

        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue;
            constraint.isUAMEnabled(value);
        }
        return value;
    }

    @Override
    public Constraint<Boolean> isAdvancedFilteringEnabled(@NonNull Constraint<Boolean> value) {
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue;
            constraint.isAdvancedFilteringEnabled(value);
        }
        return value;
    }

    @Override
    public Constraint<Boolean> isSuperBolusEnabled(@NonNull Constraint<Boolean> value) {
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue;
            constraint.isSuperBolusEnabled(value);
        }
        return value;
    }

    @Override
    public Constraint<Double> applyBasalConstraints(@NonNull Constraint<Double> absoluteRate, Profile profile) {
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constraint = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue;
            constraint.applyBasalConstraints(absoluteRate, profile);
        }
        return absoluteRate;
    }

    @Override
    public Constraint<Integer> applyBasalPercentConstraints(@NonNull Constraint<Integer> percentRate, Profile profile) {
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue;
            constrain.applyBasalPercentConstraints(percentRate, profile);
        }
        return percentRate;
    }

    @Override
    public Constraint<Double> applyBolusConstraints(@NonNull Constraint<Double> insulin) {
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue;
            constrain.applyBolusConstraints(insulin);
        }
        return insulin;
    }

    @Override
    public Constraint<Double> applyExtendedBolusConstraints(@NonNull Constraint<Double> insulin) {
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue;
            constrain.applyExtendedBolusConstraints(insulin);
        }
        return insulin;
    }

    @Override
    public Constraint<Integer> applyCarbsConstraints(@NonNull Constraint<Integer> carbs) {
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue;
            constrain.applyCarbsConstraints(carbs);
        }
        return carbs;
    }

    @Override
    public Constraint<Double> applyMaxIOBConstraints(@NonNull Constraint<Double> maxIob) {
        ArrayList<PluginBase> constraintsPlugins = MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class);
        for (PluginBase p : constraintsPlugins) {
            ConstraintsInterface constrain = (ConstraintsInterface) p;
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue;
            constrain.applyMaxIOBConstraints(maxIob);
        }
        return maxIob;
    }


}
