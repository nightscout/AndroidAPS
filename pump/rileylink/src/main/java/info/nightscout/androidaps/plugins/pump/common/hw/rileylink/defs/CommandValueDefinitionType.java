package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs;

import androidx.annotation.Nullable;

/**
 * Created by andy on 4/5/19.
 */

public interface CommandValueDefinitionType {

    String getName();


    @Nullable String getDescription();


    @Nullable String commandAction();

}
