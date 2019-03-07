package info.nightscout.androidaps.plugins.constraints.storage;

import android.os.Environment;
import android.os.StatFs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;

/**
 * Created by Rumen on 06.03.2019.
 */
public class StorageConstraintPlugin extends PluginBase implements ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(L.CONSTRAINTS);
    static StorageConstraintPlugin plugin = null;

    public static StorageConstraintPlugin getPlugin() {
        if (plugin == null)
            plugin = new StorageConstraintPlugin();
        return plugin;
    }

    public StorageConstraintPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.CONSTRAINTS)
                .neverVisible(true)
                .alwaysEnabled(true)
                .showInList(false)
                .pluginName(R.string.storage)
        );
    }

    /**
     * Constraints interface
     **/

    @Override
    public Constraint<Boolean> isClosedLoopAllowed(Constraint<Boolean> value) {
        long diskfree = getAvailableInternalMemorySize();
        log.debug("Internal storage free (Mb):"+diskfree);
        if (diskfree < 200) {
            value.set(false, MainApp.gs(R.string.diskfull), this);
        }

        return value;
    }

    public static long getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long blocksAvailable = stat.getAvailableBlocksLong();
        int size = 1048576; // blocksize of 1 Mb
        return ((blocksAvailable * blockSize) / size);
    }

}
