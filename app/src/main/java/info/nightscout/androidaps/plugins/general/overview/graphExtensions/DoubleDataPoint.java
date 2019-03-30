package info.nightscout.androidaps.plugins.general.overview.graphExtensions;

import com.jjoe64.graphview.series.DataPointInterface;

import java.io.Serializable;

/**
 * Created by mike on 21.04.2017.
 */

public class DoubleDataPoint implements DataPointInterface, Serializable {
    private static final long serialVersionUID=1428267322645L;

    private double x;
    private double y1;
    private double y2;

    public DoubleDataPoint(double x, double y1, double y2) {
        this.x=x;
        this.y1=y1;
        this.y2=y2;
    }

    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y1;
    }

    public double getY1() {
        return y1;
    }

    public double getY2() {
        return y2;
    }
}