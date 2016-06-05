package info.nightscout.androidaps.data;

/**
 * Created by mike on 05.06.2016.
 */
public class Iob {
    public double iobContrib = 0d;
    public double activityContrib = 0d;

    public Iob plus(Iob iob) {
        iobContrib += iob.iobContrib;
        activityContrib += iob.activityContrib;
        return this;
    }
}
