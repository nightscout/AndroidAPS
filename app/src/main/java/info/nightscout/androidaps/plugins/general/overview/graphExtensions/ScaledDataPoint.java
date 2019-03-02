package info.nightscout.androidaps.plugins.general.overview.graphExtensions;

import com.jjoe64.graphview.series.DataPointInterface;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by mike on 18.10.2017.
 */

public class ScaledDataPoint implements DataPointInterface, Serializable {
    private static final long serialVersionUID=1428263342645L;

    private double x;
    private double y;

    private Scale scale;

    public ScaledDataPoint(double x, double y, Scale scale) {
        this.x=x;
        this.y=y;
        this.scale = scale;
    }

    public ScaledDataPoint(Date x, double y, Scale scale) {
        this.x = x.getTime();
        this.y = y;
        this.scale = scale;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return scale.transform(y);
    }

    @Override
    public String toString() {
        return "["+x+"/"+y+"]";
    }
}
